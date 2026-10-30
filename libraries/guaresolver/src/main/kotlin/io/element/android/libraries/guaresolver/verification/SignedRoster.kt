/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The signed roster as `GET /roster` serves it, with every field the `gua-roster.v1` bytes cover. The
 * member block stays raw JSON for [MemberEntryVerifier], so a broken block fails memberEntry, not the roster.
 */
@Serializable
internal data class SignedRoster(
    val version: Long,
    val issuedAt: String,
    val entries: List<SignedRosterEntry> = emptyList(),
    val logCheckpoint: LogCheckpoint,
    val authoritySignatures: List<AuthoritySignature> = emptyList(),
)

@Serializable
internal data class SignedRosterEntry(
    val homeserver: RosterHomeserver,
    val claims: List<RosterClaim>? = null,
    val admittedAt: String? = null,
    val status: String,
    val member: JsonElement? = null,
) {
    val isActive: Boolean
        get() = status == "ACTIVE"
}

/** The four endpoint fields may be null, as the resolver's own model allows; the canonical bytes then say `null`. */
@Serializable
internal data class RosterHomeserver(
    val id: String? = null,
    val serverName: String? = null,
    val baseUrl: String? = null,
    val masIssuer: String? = null,
    val region: String? = null,
    val weight: Int = 0,
    val acceptsNew: Boolean = false,
    val signingKey: String? = null,
    val searchVisibility: String? = null,
    val searchGroups: List<String>? = null,
)

@Serializable
internal data class RosterClaim(
    val country: String? = null,
    val mccmnc: String? = null,
    val carrier: String? = null,
    val phonePrefixes: List<String>? = null,
    val affiliation: String? = null,
    val attributeMatch: Map<String, String>? = null,
    val remoteClaimUrl: String? = null,
    val priority: Int = 0,
) {
    val isRemote: Boolean
        get() = !remoteClaimUrl.isNullOrBlank()
}

@Serializable
internal data class LogCheckpoint(
    val merkleRoot: String,
    val size: Long,
) {
    init {
        require(size in 0L..Int.MAX_VALUE) { "a log size fits in 31 bits" }
    }
}

/** An entry without a key id or a value is kept, and counts as a signature that does not verify. */
@Serializable
internal data class AuthoritySignature(
    val authorityKeyId: String? = null,
    val signatureB64: String? = null,
)

/** Every field is optional here, so that a missing one fails the member check that reads it. */
@Serializable
internal data class MemberAttestation(
    val schema: String? = null,
    val alg: String? = null,
    val keyId: String? = null,
    val sequence: Long? = null,
    val notBefore: String? = null,
    val notAfter: String? = null,
    val signatures: List<MemberSignature>? = null,
)

@Serializable
internal data class MemberSignature(
    val keyId: String? = null,
    val signatureB64: String? = null,
)

@Serializable
internal data class TransparencyLog(
    val events: List<LogEvent> = emptyList(),
)

@Serializable
internal data class LogEvent(
    val index: Long,
    val type: String,
    val homeserverId: String? = null,
    val payloadHash: String,
    val recordedAt: String,
) {
    // Every event must be well formed, past the checkpoint too.
    init {
        require(index in 0L..Int.MAX_VALUE) { "a log index fits in 31 bits" }
        require(recordedAt.isResolverTimestamp()) { "a log time is in the resolver's form" }
    }

    fun leafData(): String = "$index|$type|${homeserverId.orEmpty()}|$payloadHash|${recordedAt.toEpochMillis()}"
}

@Serializable
internal data class ConsistencyProof(
    val proof: List<String>,
) {
    init {
        require(proof.all { PROOF_HASH.matches(it) }) { "a proof hash is 64 lowercase hex characters" }
    }
}

private val PROOF_HASH = Regex("[0-9a-f]{64}")
