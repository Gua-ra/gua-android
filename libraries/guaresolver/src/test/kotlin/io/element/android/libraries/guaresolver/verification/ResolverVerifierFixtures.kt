/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.guaresolver.FakeGuaDeployment
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.genesis.CachingPreferenceDataStoreFactory
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

private val vectors: JsonObject by lazy { VectorFixtures.load(VectorFixtures.CLIENT_VECTORS_RESOURCE) }
private val json = Json { ignoreUnknownKeys = true }

/** Roster r1, whose checkpoint precedes every POLICY_PUBLISH leaf, and its log: no policy is due. */
internal fun withoutPolicy() = ScriptedResolver().apply {
    roster = { ok(servedRoster("r1").toString()) }
    log = { ok(logJson()) }
}

/** Roster r2 with the signed policy, under which [A_PHONE] is placed on hs-br. */
internal fun policyRoutingToBr() = ScriptedResolver().apply {
    roster = { ok(servedRoster("r2").toString()) }
    policy = { ok(servedPolicy(BR_POLICY).toString()) }
    log = { ok(logJson()) }
}

internal suspend fun verify(verifier: DefaultResolverVerifier, resolution: HomeserverResolution, phone: String = A_PHONE) =
    checkNotNull(verifier.verify(phone, resolution))

internal fun createVerifier(
    server: MockWebServer,
    trustRoots: String? = allRoots(),
    dataStoreFactory: PreferenceDataStoreFactory = CachingPreferenceDataStoreFactory(),
    now: String = PLACEMENT_TIME,
    resolverBaseUrl: String? = server.url("/").toString(),
) = DefaultResolverVerifier(
    retrofitFactory = RetrofitFactory(
        okHttpClient = { OkHttpClient.Builder().build() },
        json = { DefaultJsonProvider() },
    ),
    dispatchers = CoroutineDispatchers(io = Dispatchers.IO, computation = Dispatchers.Default, main = Dispatchers.Default),
    systemClock = SystemClock { Instant.parse(now).toEpochMilli() },
    preferenceDataStoreFactory = dataStoreFactory,
    deployment = FakeGuaDeployment(resolverBaseUrl = resolverBaseUrl, resolverTrustRoots = trustRoots),
)

internal fun authorityRoots(): String = "authority:authority-a:${spki("rfc8032-test1")} authority:authority-b:${spki("rfc8032-test2")}"

internal fun allRoots(): String = "${authorityRoots()} policy:governance-a:${spki("rfc8032-test3")}"

internal fun spki(keyRef: String): String = vectors["keys"]!!.jsonObject[keyRef]!!.jsonObject.string("publicKeySpkiB64")

internal fun seed(keyRef: String): String = vectors["keys"]!!.jsonObject[keyRef]!!.jsonObject.string("seedHex")

internal fun servedRoster(ref: String): JsonObject = vectors.byRef("rosters", ref)["roster"]!!.jsonObject

internal fun servedPolicy(ref: String): JsonObject = vectors.byRef("policies", ref)["bundle"]!!.jsonObject

internal fun rosterRejection(index: Int): JsonObject = vectors["rosterRejections"]!!.jsonArray[index].jsonObject["roster"]!!.jsonObject

internal fun policyRejection(index: Int): JsonObject = vectors["policyRejections"]!!.jsonArray[index].jsonObject["bundle"]!!.jsonObject

internal fun JsonObject.rosterEntries(): List<JsonObject> = this["entries"]!!.jsonArray.map { it.jsonObject }

/** A one-entry roster with that entry's member block replaced; the authority signature does not cover it. */
internal fun withMember(roster: JsonObject, member: JsonElement): JsonObject =
    JsonObject(roster + ("entries" to JsonArray(listOf(JsonObject(roster.rosterEntries().single() + ("member" to member))))))

internal fun withCheckpoint(roster: JsonObject, size: Long, merkleRoot: String = rootAt(size.toInt())): JsonObject {
    val checkpoint = buildJsonObject {
        put("merkleRoot", merkleRoot)
        put("size", size)
    }
    return JsonObject(roster + ("logCheckpoint" to checkpoint))
}

/** [roster] signed again by authority-a alone, over the bytes of [form]. */
internal fun resigned(roster: JsonObject, form: RosterForm = RosterForm.V1): JsonObject {
    val canonical = CanonicalRoster.bytes(json.decodeFromJsonElement<SignedRoster>(roster), form)
    val signature = buildJsonObject {
        put("authorityKeyId", "authority-a")
        put("signatureB64", VectorFixtures.sign(seed("rfc8032-test1"), canonical))
    }
    return JsonObject(roster + ("authoritySignatures" to JsonArray(listOf(signature))))
}

/** [bundle] signed again by governance-a alone; its delegate signatures no longer match, which these tests do not need. */
internal fun resignedPolicy(bundle: JsonObject): JsonObject {
    val canonical = CanonicalRoutingPolicy.bytes(json.decodeFromJsonElement<RoutingPolicyBundle>(bundle))
    val signature = buildJsonObject {
        put("authorityKeyId", "governance-a")
        put("signatureB64", VectorFixtures.sign(seed("rfc8032-test3"), canonical))
    }
    return JsonObject(bundle + ("signatures" to JsonArray(listOf(signature))))
}

/** The member block of [entry], signed again by the member's own test key for [homeserver]. */
internal fun memberSignedFor(homeserver: JsonObject, entry: JsonObject): JsonObject {
    val member = entry["member"]!!.jsonObject
    val attestation = Json.decodeFromJsonElement<MemberAttestation>(member)
    val canonical = CanonicalMemberEntry.bytes(json.decodeFromJsonElement<RosterHomeserver>(homeserver), attestation)
    val signature = buildJsonObject {
        put("keyId", attestation.keyId)
        put("signatureB64", VectorFixtures.sign(seed("rfc8032-test-sha-abc"), canonical))
    }
    return JsonObject(member + ("signatures" to JsonArray(listOf(signature))))
}

internal fun logEvents(): List<JsonElement> = vectors["log"]!!.jsonObject["events"]!!.jsonArray

internal fun rootAt(size: Int): String =
    vectors["log"]!!.jsonObject["roots"]!!.jsonArray.map { it.jsonObject }.single { it.int("size") == size }.string("rootHex")

internal fun logJson(events: List<JsonElement> = logEvents()): String = buildJsonObject {
    put(
        "head",
        buildJsonObject {
            put("merkleRoot", rootAt(events.size))
            put("size", events.size)
        },
    )
    put("events", JsonArray(events))
}.toString()

internal fun consistencyJson(first: Int, second: Int, tampered: Boolean = false, uppercase: Boolean = false): String {
    val vector = vectors["log"]!!.jsonObject["consistencyProofs"]!!.jsonArray.map { it.jsonObject }
        .first { it.int("first") == first && it.int("second") == second }
    val proof = vector["proof"]!!.jsonArray.map { it.jsonPrimitive.content }.map { if (uppercase) it.uppercase() else it }.toMutableList()
    if (tampered) proof[proof.lastIndex] = proof.last().replaceRange(0, 1, if (proof.last().startsWith("0")) "1" else "0")
    return buildJsonObject {
        put("first", first)
        put("second", second)
        put("proof", JsonArray(proof.map { JsonPrimitive(it) }))
    }.toString()
}

internal fun deepArrays(): String = "[".repeat(DEEP_NESTING) + "]".repeat(DEEP_NESTING)

/** Runs [block] with a default handler that records what reaches it from any thread, OkHttp's included. */
internal suspend fun <T> recordingUncaughtExceptions(block: suspend () -> T): Pair<T, List<Throwable>> {
    val recorded = CopyOnWriteArrayList<Throwable>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, throwable -> recorded += throwable }
    try {
        return block() to recorded.toList()
    } finally {
        Thread.setDefaultUncaughtExceptionHandler(previous)
    }
}

internal fun answer(homeserver: ResolvedHomeserver, exists: Boolean = false, rosterVersion: Long? = null) =
    HomeserverResolution(exists = exists, homeserver = homeserver, rosterVersion = rosterVersion)

internal fun hsBr() = ResolvedHomeserver(
    serverName = "br.gua.test",
    baseUrl = "https://matrix.br.gua.test",
    masIssuer = "https://account.br.gua.test",
    region = "BR",
)

internal fun hsCa() = ResolvedHomeserver(
    serverName = "ca.gua.test",
    baseUrl = "https://matrix.ca.gua.test",
    masIssuer = "https://account.ca.gua.test",
    region = null,
)

internal fun ok(body: String) = MockResponse().setBody(body)

/** Answers each artifact path from a lambda, so a test rewrites what is served between runs. */
internal class ScriptedResolver {
    var roster: () -> MockResponse = { notFound() }
    var log: () -> MockResponse = { notFound() }
    var consistency: (first: Int, second: Int) -> MockResponse = { _, _ -> notFound() }
    var policy: () -> MockResponse = { notFound() }
    val requests: MutableList<String> = CopyOnWriteArrayList()

    fun start(): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                return when (path.substringBefore('?')) {
                    "/roster" -> roster()
                    "/roster/log" -> log()
                    "/roster/log/consistency" -> consistency(
                        request.requestUrl!!.queryParameter("first")!!.toInt(),
                        request.requestUrl!!.queryParameter("second")!!.toInt(),
                    )
                    "/policy/routing" -> policy()
                    else -> notFound()
                }
            }
        }
        start()
    }

    private fun notFound() = MockResponse().setResponseCode(404)
}

/** A store whose every read and write throws [failure], by default the IOException of a disk that is gone. */
internal class FailingPreferenceDataStoreFactory(private val failure: () -> Exception = { IOException("unavailable") }) : PreferenceDataStoreFactory {
    override fun create(name: String): DataStore<Preferences> = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure() }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw failure()
    }
}

internal const val A_PHONE = "+5511987654321"
internal const val A_CANADIAN_PHONE = "+14165550123"
internal const val A_PHONE_PLACED_ON_BR = "+5521990001237"
internal const val BR_POLICY = "br-policy v3"
internal const val PLACEMENT_TIME = "2026-10-27T00:00:00Z"
internal const val STORE_NAME = "gua_resolver_verification"
internal const val TAMPERED_BASE_URL_REJECTION = 0
internal const val UNTRUSTED_KEY_REJECTION = 1
internal const val MIXED_ROSTER_REJECTION = 5
internal const val OPERATIONAL_KEY_REJECTION = 0
internal const val NESTED = "NESTED"
internal const val DEEP_NESTING = 100_000
internal const val PROMPT_MILLIS = 5_000L
internal const val SLOW_PROOF_MILLIS = 300L
internal const val BUDGET_SECONDS = 10L
