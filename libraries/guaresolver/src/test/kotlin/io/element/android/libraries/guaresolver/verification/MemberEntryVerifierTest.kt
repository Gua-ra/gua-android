/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** The member block checks the vectors do not reach, over the first published member entry. */
class MemberEntryVerifierTest {
    private val vector: JsonObject by lazy { VectorFixtures.load(VectorFixtures.LP_VECTORS_RESOURCE)["memberEntries"]!!.jsonArray.first().jsonObject }
    private val json = Json { ignoreUnknownKeys = true }
    private val homeserver: RosterHomeserver by lazy { json.decodeFromJsonElement(vector["homeserver"]!!) }
    private val member: JsonObject by lazy { vector["member"]!!.jsonObject }
    private val at: Long by lazy { vector.long("notBeforeEpochMs") }

    @Test
    fun `the published block is valid`() {
        assertThat(MemberEntryVerifier.verify(homeserver, member, at)).isEqualTo(MemberEntryOutcome.Valid)
    }

    @Test
    fun `a window longer than 400 days is bad_window even where its milliseconds would wrap`() {
        val window = memberWith("notBefore" to JsonPrimitive("-158000000-01-01T00:00:00Z"), "notAfter" to JsonPrimitive("+158000000-01-01T00:00:00Z"))

        assertThat(MemberEntryVerifier.verify(homeserver, window, at)).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_WINDOW))
    }

    @Test
    fun `a bound past the epoch-millisecond range is bad_window`() {
        val window = memberWith("notAfter" to JsonPrimitive("+300000000-01-01T00:00:00Z"))

        assertThat(MemberEntryVerifier.verify(homeserver, window, at)).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_WINDOW))
    }

    @Test
    fun `a field sent as null is parse_error`() {
        val block = memberWith("sequence" to JsonNull)

        assertThat(MemberEntryVerifier.verify(homeserver, block, at)).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.PARSE_ERROR))
    }

    @Test
    fun `a field left out fails the check that reads it`() {
        val block = JsonObject(member - "sequence")

        assertThat(MemberEntryVerifier.verify(homeserver, block, at)).isEqualTo(MemberEntryOutcome.Invalid(VerificationReason.BAD_SEQUENCE))
    }

    private fun memberWith(vararg fields: Pair<String, JsonElement>) = JsonObject(member + fields)
}
