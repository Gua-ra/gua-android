/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The signed roster as `GET /roster` serves it, with every field the `gua-roster.v1` bytes cover. The
 * document is read leniently; the member block stays a [JsonObject] so that [MemberEntryVerifier] can
 * parse it strictly, since the member signature covers exactly the fields that block declares.
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
    // A JsonObject keeps the last of duplicate keys; the member signature is then checked over the values read.
    val member: JsonObject? = null,
) {
    val isActive: Boolean
        get() = status == "ACTIVE"
}

@Serializable
internal data class RosterHomeserver(
    val id: String,
    val serverName: String,
    val baseUrl: String,
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
)

@Serializable
internal data class AuthoritySignature(
    val authorityKeyId: String,
    val signatureB64: String,
)

@Serializable
internal data class MemberAttestation(
    val schema: String,
    val alg: String,
    val keyId: String,
    val sequence: Long,
    val notBefore: String,
    val notAfter: String,
    val signatures: List<MemberSignature>,
)

@Serializable
internal data class MemberSignature(
    val keyId: String,
    val signatureB64: String,
)

@Serializable
internal data class TransparencyLog(
    val head: LogCheckpoint? = null,
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
    fun leafData(): String = "$index|$type|${homeserverId.orEmpty()}|$payloadHash|${recordedAt.toEpochMillis()}"
}

@Serializable
internal data class ConsistencyProof(
    val first: Long,
    val second: Long,
    val proof: List<String> = emptyList(),
)
