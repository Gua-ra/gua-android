/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** The published vector files, byte-identical copies of the resolver's `docs/specs` files. */
internal object VectorFixtures {
    const val CLIENT_VECTORS_RESOURCE = "gua-client-verification-v1-vectors.json"
    const val LP_VECTORS_RESOURCE = "gua-lp-v1-vectors.json"

    private const val PKCS8_PREFIX = "302e020100300506032b657004220420"

    fun load(resource: String): JsonObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "$resource is missing from the test resources" }
        return Json.parseToJsonElement(stream.reader().readText()).jsonObject
    }

    /** Signs with the JDK Ed25519 provider, independent of what the app verifies with. */
    fun sign(seedHex: String, message: ByteArray): String {
        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec((PKCS8_PREFIX + seedHex).hexToBytes()))
        return Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(message)
            Base64.getEncoder().encodeToString(sign())
        }
    }
}

/** The vector of [section] whose `ref` is [ref]: vectors name the rosters and policies they use this way. */
internal fun JsonObject.byRef(section: String, ref: String): JsonObject =
    this[section]!!.jsonArray.map { it.jsonObject }.single { it.stringOrNull("ref") == ref }

/** An lp vector homeserver carries the member-signed fields only, so the roster's weight and acceptsNew are added. */
internal fun JsonObject.withRosterAttributes(): JsonObject = JsonObject(mapOf("weight" to JsonPrimitive(0), "acceptsNew" to JsonPrimitive(false)) + this)

internal fun JsonObject.string(key: String): String = this[key]!!.jsonPrimitive.content

internal fun JsonObject.stringOrNull(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

internal fun JsonObject.int(key: String): Int = this[key]!!.jsonPrimitive.content.toInt()

internal fun JsonObject.long(key: String): Long = this[key]!!.jsonPrimitive.content.toLong()

internal fun JsonObject.bool(key: String): Boolean = this[key]!!.jsonPrimitive.boolean

internal fun String.hexToBytes(): ByteArray = checkNotNull(Hex.decode(this)) { "not a hex string" }

internal fun ByteArray.toHex(): String = Hex.encode(this)
