/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException
import kotlin.coroutines.resume

/** One fetched document: its decoded body, a 404, a body that is not the expected JSON, or any other failure. */
internal sealed interface Fetched<out T> {
    data class Ok<T>(val value: T) : Fetched<T>
    data object NotFound : Fetched<Nothing>
    data object Malformed : Fetched<Nothing>
    data object Failed : Fetched<Nothing>
}

/** The roster and the form its served JSON dictates, which the decoded roster can no longer tell. */
internal data class ServedRoster(val form: RosterForm, val roster: SignedRoster)

/**
 * The public resolver documents a verifier reads, over the unlogged client. Each body is read raw on
 * OkHttp's thread, at most [MAX_BODY_BYTES] of it, and decoded in the caller's coroutine once its nesting is
 * known to be shallow, so no body can end the app on a thread whose errors nothing catches.
 */
internal class ResolverDocuments(retrofit: Retrofit) {
    private val calls = retrofit.callFactory()
    private val baseUrl = retrofit.baseUrl()

    suspend fun roster(): Fetched<ServedRoster> = text("roster").decode { body ->
        val tree = json.decodeFromString<JsonObject>(body)
        ServedRoster(CanonicalRoster.formOf(tree), json.decodeFromJsonElement<SignedRoster>(tree))
    }

    suspend fun log(): Fetched<TransparencyLog> = text("roster/log").decode { json.decodeFromString<TransparencyLog>(it) }

    suspend fun consistency(first: Long, second: Long): Fetched<ConsistencyProof> =
        text("roster/log/consistency?first=$first&second=$second").decode { json.decodeFromString<ConsistencyProof>(it) }

    /** [Fetched.NotFound] when no policy is served. */
    suspend fun routingPolicy(): Fetched<RoutingPolicyBundle> = text("policy/routing").decode { json.decodeFromString<RoutingPolicyBundle>(it) }

    private suspend fun text(path: String): Fetched<String> {
        val url = baseUrl.resolve(path) ?: return Fetched.Failed
        val call = calls.newCall(Request.Builder().url(url).build())
        return suspendCancellableCoroutine { continuation ->
            // Cancelling the call also aborts a body read in progress, so the run's budget holds.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(Fetched.Failed)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response.use { it.cappedText() })
                }
            })
        }
    }
}

private const val MAX_BODY_BYTES = 2L * 1024 * 1024
private const val MAX_NESTING = 64
private const val HTTP_NOT_FOUND = 404

// Strict syntax, unlike the app's instance. Unknown fields are ignored here and refused in a member block.
private val json = Json { ignoreUnknownKeys = true }

/** The body of a 2xx answer, refused unread past the cap. This runs on OkHttp's thread, so it never throws. */
private fun Response.cappedText(): Fetched<String> = try {
    val source = body.source()
    when {
        code == HTTP_NOT_FOUND -> Fetched.NotFound
        !isSuccessful -> Fetched.Failed
        source.request(MAX_BODY_BYTES + 1) -> Fetched.Malformed
        else -> Fetched.Ok(source.readUtf8())
    }
} catch (e: Exception) {
    Fetched.Failed
}

private inline fun <T> Fetched<String>.decode(parse: (String) -> T): Fetched<T> = when (this) {
    is Fetched.Ok -> try {
        if (isNestedAtMost(value, MAX_NESTING)) Fetched.Ok(parse(value)) else Fetched.Malformed
    } catch (e: IllegalArgumentException) {
        Fetched.Malformed
    }
    Fetched.NotFound -> Fetched.NotFound
    Fetched.Malformed -> Fetched.Malformed
    Fetched.Failed -> Fetched.Failed
}

/** No array or object nests deeper than [limit]; brackets inside strings are text. Iterative, so any input is safe. */
private fun isNestedAtMost(text: String, limit: Int): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in text) {
        when {
            escaped -> escaped = false
            inString && c == '\\' -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '[' || c == '{' -> if (++depth > limit) return false
            c == ']' || c == '}' -> depth--
        }
    }
    return true
}
