/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import java.io.IOException
import kotlin.coroutines.resume

internal sealed interface Fetched<out T> {
    data class Ok<T>(val value: T) : Fetched<T>
    data object NotFound : Fetched<Nothing>
    data object Malformed : Fetched<Nothing>
    data object Failed : Fetched<Nothing>
}

internal data class ServedRoster(val form: RosterForm, val roster: SignedRoster)

/**
 * Bodies are read raw and capped on OkHttp's thread, then decoded in the caller's coroutine once their nesting is
 * known to be shallow, so no body can end the app on a thread whose errors nothing catches.
 */
internal class ResolverDocuments(retrofit: Retrofit) {
    private val calls = retrofit.callFactory()
    private val baseUrl = retrofit.baseUrl()

    suspend fun roster(): Fetched<ServedRoster> = text("roster").decode { body ->
        val tree = json.decodeFromString<JsonObject>(body)
        require(!tree.rosterQuotesAScalar()) { "a roster number or boolean is a string" }
        ServedRoster(CanonicalRoster.formOf(tree), json.decodeFromJsonElement<SignedRoster>(tree))
    }

    suspend fun log(): Fetched<TransparencyLog> = text("roster/log").decode { body ->
        val tree = json.decodeFromString<JsonObject>(body)
        require(tree["events"].objects().none { it.quotes("index") }) { "a log index is a string" }
        json.decodeFromJsonElement<TransparencyLog>(tree)
    }

    suspend fun consistency(first: Long, second: Long): Fetched<ConsistencyProof> =
        text("roster/log/consistency?first=$first&second=$second").decode { json.decodeFromString<ConsistencyProof>(it) }

    suspend fun routingPolicy(): Fetched<RoutingPolicyBundle> = text("policy/routing").decode { body ->
        val tree = json.decodeFromString<JsonObject>(body)
        require(!tree.policyQuotesAScalar()) { "a policy number or boolean is a string" }
        json.decodeFromJsonElement<RoutingPolicyBundle>(tree)
    }

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

// Strict syntax, unlike the app's instance.
private val json = Json { ignoreUnknownKeys = true }

/** Runs on OkHttp's thread, so it never throws. A body past the cap is refused unread. */
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

/** Whether any of these fields is a JSON string, which kotlinx would still read as the number or boolean it names. */
internal fun JsonObject.quotes(vararg fields: String): Boolean = fields.any { (this[it] as? JsonPrimitive)?.isString == true }

private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

private fun JsonObject.rosterQuotesAScalar(): Boolean =
    quotes("version") ||
        (this["logCheckpoint"] as? JsonObject)?.quotes("size") == true ||
        this["entries"].objects().any { entry ->
            (entry["homeserver"] as? JsonObject)?.quotes("weight", "acceptsNew") == true || entry["claims"].objects().any { it.quotes("priority") }
        }

private fun JsonObject.policyQuotesAScalar(): Boolean =
    quotes("version") || this["rules"].objects().any { it.quotes("priority", "enabled") } || (this["fallback"] as? JsonObject)?.quotes("enabled") == true

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
