/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.core.data.tryOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

/** The `gua-member-entry.v1` bytes a homeserver signs about its own entry. */
internal object CanonicalMemberEntry {
    const val SCHEMA = "gua-member-entry.v1"
    const val ALG = "Ed25519"

    fun bytes(homeserver: RosterHomeserver, member: MemberAttestation): ByteArray =
        CanonicalEncoder.begin(SCHEMA)
            .string(required(homeserver.id, "id"))
            .string(required(homeserver.serverName, "serverName"))
            .string(required(homeserver.baseUrl, "baseUrl"))
            .string(required(homeserver.masIssuer, "masIssuer"))
            .string(required(member.alg, "alg"))
            .string(required(homeserver.signingKey, "signingKey"))
            .string(required(member.keyId, "keyId"))
            .int64(required(member.sequence, "sequence"))
            .int64(required(member.notBefore, "notBefore").toEpochMillis())
            .int64(required(member.notAfter, "notAfter").toEpochMillis())
            .optionalString(homeserver.region)
            .string(homeserver.searchVisibility ?: "GLOBAL")
            .stringSet(homeserver.searchGroups.orEmpty())
            .toByteArray()

    private fun <T : Any> required(value: T?, field: String): T = value ?: throw CanonicalEncodingException("$field is required")
}

/**
 * Verifies a homeserver's self-signature over its roster entry, port of the resolver's
 * `MemberEntryVerifier`. v1 keeps no earlier entry per homeserver, so key continuity is not checked.
 */
internal class MemberEntryVerifier(
    private val maxLifetimeMillis: Long = DEFAULT_MAX_LIFETIME_MILLIS,
) {
    fun verify(homeserver: RosterHomeserver, member: JsonElement?, atMillis: Long): MemberEntryOutcome {
        if (member == null || member is JsonNull) return MemberEntryOutcome.Unattested
        val attestation = decode(member) ?: return MemberEntryOutcome.Invalid(VerificationReason.PARSE_ERROR)
        val sequence = attestation.sequence
        if (attestation.schema != CanonicalMemberEntry.SCHEMA) return MemberEntryOutcome.Invalid(VerificationReason.UNSUPPORTED_SCHEMA)
        if (attestation.alg != CanonicalMemberEntry.ALG) return MemberEntryOutcome.Invalid(VerificationReason.UNSUPPORTED_ALG)
        if (attestation.keyId.isNullOrBlank()) return MemberEntryOutcome.Invalid(VerificationReason.BAD_KEY_ID)
        if (sequence == null || sequence < 1) return MemberEntryOutcome.Invalid(VerificationReason.BAD_SEQUENCE)
        checkWindow(attestation, atMillis)?.let { return MemberEntryOutcome.Invalid(it) }
        if (!hasWellFormedSignatures(attestation)) return MemberEntryOutcome.Invalid(VerificationReason.MALFORMED_SIGNATURE)

        val canonical = try {
            CanonicalMemberEntry.bytes(homeserver, attestation)
        } catch (e: CanonicalEncodingException) {
            return MemberEntryOutcome.Invalid(VerificationReason.NO_CANONICAL_ENCODING)
        }
        val publicKey = homeserver.signingKey?.let { Ed25519Signatures.rawPublicKey(it) }
            ?: return MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNING_KEY)
        val own = attestation.signatures.orEmpty().firstOrNull { it.keyId == attestation.keyId }
            ?: return MemberEntryOutcome.Invalid(VerificationReason.MISSING_SIGNATURE)
        if (!Ed25519Signatures.verify(publicKey, canonical, own.signatureB64)) {
            return MemberEntryOutcome.Invalid(VerificationReason.BAD_SIGNATURE)
        }
        return MemberEntryOutcome.Valid
    }

    // The default Json refuses unknown fields, so a block cannot carry a field its signature does not cover.
    private fun decode(member: JsonElement): MemberAttestation? =
        (member as? JsonObject)?.let { tryOrNull { Json.decodeFromJsonElement<MemberAttestation>(it) } }

    private fun checkWindow(member: MemberAttestation, atMillis: Long): String? {
        if (member.notBefore == null || member.notAfter == null) return VerificationReason.BAD_WINDOW
        val notBefore = tryOrNull { Instant.parse(member.notBefore) } ?: return VerificationReason.PARSE_ERROR
        val notAfter = tryOrNull { Instant.parse(member.notAfter) } ?: return VerificationReason.PARSE_ERROR
        if (notBefore.nano % NANOS_PER_MILLI != 0 || notAfter.nano % NANOS_PER_MILLI != 0) return VerificationReason.BAD_WINDOW
        val notBeforeMillis = notBefore.toEpochMilli()
        val notAfterMillis = notAfter.toEpochMilli()
        if (notAfterMillis <= notBeforeMillis) return VerificationReason.BAD_WINDOW
        if (notAfterMillis - notBeforeMillis > maxLifetimeMillis) return VerificationReason.BAD_WINDOW
        if (atMillis < notBeforeMillis || atMillis > notAfterMillis) return VerificationReason.OUTSIDE_WINDOW
        return null
    }

    /** At least one signature, each with a key id and a value, and no key id twice. */
    private fun hasWellFormedSignatures(member: MemberAttestation): Boolean {
        val signatures = member.signatures.orEmpty()
        val keyIds = signatures.map { it.keyId }
        return signatures.isNotEmpty() &&
            signatures.all { !it.keyId.isNullOrBlank() && it.signatureB64 != null } &&
            keyIds.distinct().size == keyIds.size
    }

    companion object {
        const val DEFAULT_MAX_LIFETIME_MILLIS: Long = 400L * 24 * 60 * 60 * 1000
        private const val NANOS_PER_MILLI = 1_000_000
    }
}
