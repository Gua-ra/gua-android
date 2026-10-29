/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * The `gua-roster.v1` bytes the authority signs: a port of the resolver's `CanonicalRoster`, byte for
 * byte. Entries are sorted by id in UTF-16 code unit order (Kotlin's `String.compareTo`, like Java's) and
 * fields are joined by U+001F, which the resolver asserts is absent from every value.
 */
internal object CanonicalRoster {
    private const val FIELD_SEPARATOR = "\u001f"

    /**
     * The form follows the served JSON, by key presence, because the decoded roster cannot tell an absent
     * `searchVisibility` from a null one: the search fields are encoded only when every entry has the key.
     */
    fun formOf(roster: JsonObject): RosterForm {
        val homeservers = (roster["entries"] as? JsonArray).orEmpty().map { (it as? JsonObject)?.get("homeserver") as? JsonObject }
        val carrying = homeservers.count { it?.containsKey("searchVisibility") == true }
        return when (carrying) {
            0 -> RosterForm.LEGACY
            homeservers.size -> RosterForm.V1
            else -> RosterForm.MIXED
        }
    }

    fun bytes(roster: SignedRoster, form: RosterForm): ByteArray {
        require(form != RosterForm.MIXED) { "a mixed roster has no canonical bytes" }
        val out = StringBuilder()
        out.append("gua-roster.v1\n")
        out.append("version=").append(roster.version).append('\n')
        out.append("issuedAt=").append(roster.issuedAt.toEpochMillis()).append('\n')
        out.append("log=").append(roster.logCheckpoint.merkleRoot).append(':').append(roster.logCheckpoint.size).append('\n')
        roster.entries
            .sortedBy { it.homeserver.id }
            .forEach { out.append(entry(it, searchFields = form == RosterForm.V1)).append('\n') }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun entry(entry: SignedRosterEntry, searchFields: Boolean): String {
        val homeserver = entry.homeserver
        // StringJoiner.add(null) on the resolver side writes the literal "null", not an empty string.
        val fields = mutableListOf(
            homeserver.id ?: "null",
            homeserver.serverName ?: "null",
            homeserver.baseUrl ?: "null",
            homeserver.masIssuer ?: "null",
            homeserver.region.orEmpty(),
            homeserver.weight.toString(),
            homeserver.acceptsNew.toString(),
            homeserver.signingKey.orEmpty(),
        )
        if (searchFields) {
            fields += homeserver.searchVisibility ?: "GLOBAL"
            fields += homeserver.searchGroups.orEmpty().sorted().joinToString("|")
        }
        fields += entry.admittedAt?.toEpochMillis()?.toString().orEmpty()
        fields += entry.status
        fields += claims(entry.claims)
        return fields.joinToString(FIELD_SEPARATOR)
    }

    private fun claims(claims: List<RosterClaim>?): String {
        if (claims.isNullOrEmpty()) return "[]"
        return claims
            .sortedWith(
                compareBy<RosterClaim> { it.priority }
                    .thenBy { it.country.orEmpty() }
                    .thenBy { it.mccmnc.orEmpty() }
                    .thenBy { it.affiliation.orEmpty() }
            )
            .joinToString(";") { claim(it) }
    }

    private fun claim(claim: RosterClaim): String = listOf(
        "country=" + claim.country.orEmpty(),
        "mccmnc=" + claim.mccmnc.orEmpty(),
        "carrier=" + claim.carrier.orEmpty(),
        "prefixes=" + claim.phonePrefixes?.sorted()?.joinToString("|").orEmpty(),
        "affiliation=" + claim.affiliation.orEmpty(),
        "attrs=" + attributes(claim.attributeMatch),
        "remote=" + claim.remoteClaimUrl.orEmpty(),
        "priority=" + claim.priority,
    ).joinToString(",")

    private fun attributes(attributes: Map<String, String>?): String {
        if (attributes.isNullOrEmpty()) return ""
        return attributes.toSortedMap().entries.joinToString("|") { (key, value) -> "$key=$value" }
    }
}
