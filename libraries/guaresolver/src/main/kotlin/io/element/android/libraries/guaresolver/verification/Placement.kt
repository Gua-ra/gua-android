/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * What the placement rules may use. The app knows the phone only; affiliations and attributes count for
 * institution routing solely when they arrived in a signature-verified envelope, which a public caller
 * cannot produce, so [claimsVerified] is false here.
 */
internal data class PlacementContext(
    val e164Phone: String?,
    val country: String? = null,
    val mccmnc: String? = null,
    val carrier: String? = null,
    val regionHint: String? = null,
    val affiliations: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val claimsVerified: Boolean = false,
) {
    /** The bucketing key of the weighted fallback. Not injective and not signed; it only seeds a hash. */
    fun routingKey(): String = buildString {
        append("phone=").append(e164Phone.orEmpty()).append('\n')
        append("country=").append(country.orEmpty()).append('\n')
        append("mccmnc=").append(mccmnc.orEmpty()).append('\n')
        append("carrier=").append(carrier.orEmpty()).append('\n')
        append("region=").append(regionHint.orEmpty()).append('\n')
        append("affiliations=").append(affiliations.sorted().joinToString("|")).append('\n')
        append("attrs=")
        attributes.toSortedMap().forEach { (key, value) -> append(key).append('=').append(value).append('|') }
    }
}

internal data class PlacementDecision(
    val homeserverId: String,
    val rule: String,
    val ruleId: String,
)

/**
 * The resolver's new-account placement, reproduced over a verified roster and policy: signed policy rules,
 * then roster claims, then the stable weighted fallback. Port of `PolicyRoutingRule`, `ClaimRule` and
 * `WeightedFallbackRule`, in that order.
 */
internal object PlacementEngine {
    fun decide(
        context: PlacementContext,
        roster: SignedRoster,
        policy: RoutingPolicyBundle?,
        delegateVerifiedZones: Set<String>,
        nowMillis: Long,
    ): PlacementDecision? {
        val accepting = roster.entries.filter { it.isActive && it.homeserver.acceptsNew }
        return policy?.let { policyRule(context, it, delegateVerifiedZones, accepting, nowMillis) }
            ?: claimRule(context, accepting)
            ?: weightedFallback(context, roster.version, accepting.map { it.homeserver })
    }

    /** The first four bytes of SHA-256 over the roster version, the routing key and the candidates, as a signed int. */
    fun stableHash(context: PlacementContext, rosterVersion: Long, candidates: List<RosterHomeserver>): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("weighted-fallback.v1\nroster=$rosterVersion\n".toByteArray(Charsets.UTF_8))
        digest.update(context.routingKey().toByteArray(Charsets.UTF_8))
        for (candidate in candidates) {
            digest.update("\n${candidate.id}:${maxOf(0, candidate.weight)}".toByteArray(Charsets.UTF_8))
        }
        return ByteBuffer.wrap(digest.digest()).int
    }

    private fun policyRule(
        context: PlacementContext,
        policy: RoutingPolicyBundle,
        delegateVerifiedZones: Set<String>,
        accepting: List<SignedRosterEntry>,
        nowMillis: Long,
    ): PlacementDecision? {
        val targets = accepting.associateBy { it.homeserver.id }
        // The first zone with an id wins a duplicate, as the resolver's merge function keeps it.
        val zones = mutableMapOf<String, DelegationZone>()
        for (zone in policy.delegationZones.orEmpty()) {
            val zoneId = zone.id ?: continue
            zones.getOrPut(zoneId) { zone }
        }
        return CanonicalRoutingPolicy.sortedRules(policy.rules)
            .filter { rule -> rule.isEnabled && rule.zoneApplies(zones, delegateVerifiedZones, nowMillis) && rule.matches(context) }
            .firstNotNullOfOrNull { rule ->
                rule.targetHomeserverId?.let { targets[it] }?.let { PlacementDecision(it.homeserver.id, "PolicyRoutingRule", rule.id.orEmpty()) }
            }
    }

    /** A rule applies only inside a zone that is delegate-signed and within its validity window. */
    private fun RoutingPolicyRule.zoneApplies(zones: Map<String, DelegationZone>, delegateVerifiedZones: Set<String>, nowMillis: Long): Boolean {
        val zoneId = delegatedZoneId ?: return false
        return zoneId in delegateVerifiedZones && zones[zoneId]?.isActiveAt(nowMillis) == true
    }

    private fun RoutingPolicyRule.matches(context: PlacementContext): Boolean = when (matchType) {
        "PHONE_PREFIX" -> matchValue != null && context.e164Phone?.startsWith(matchValue) == true
        // Institution and OIDC rules need signature-verified claims, which a phone-only context never has.
        else -> false
    }

    private fun claimRule(context: PlacementContext, accepting: List<SignedRosterEntry>): PlacementDecision? =
        accepting
            .flatMap { entry -> entry.claims.orEmpty().filter { !it.isRemote && it.matchesLocally(context) }.map { entry to it } }
            .minWithOrNull(compareBy<Pair<SignedRosterEntry, RosterClaim>> { it.second.priority }.thenBy { it.first.homeserver.id })
            ?.let { (entry, claim) -> PlacementDecision(entry.homeserver.id, "ClaimRule", "roster-claim:${entry.homeserver.id}:${claim.priority}") }

    private fun RosterClaim.matchesLocally(context: PlacementContext): Boolean {
        if (country != null && !country.equals(context.country, ignoreCase = true)) return false
        if (mccmnc != null && mccmnc != context.mccmnc) return false
        if (carrier != null && (context.carrier == null || !carrier.equals(context.carrier, ignoreCase = true))) return false
        if (!phonePrefixes.isNullOrEmpty()) {
            val phone = context.e164Phone ?: return false
            if (phonePrefixes.none { phone.startsWith(it) }) return false
        }
        if (affiliation != null && (!context.claimsVerified || affiliation !in context.affiliations)) return false
        if (!attributeMatch.isNullOrEmpty()) {
            if (!context.claimsVerified) return false
            if (attributeMatch.any { (key, value) -> context.attributes[key] != value }) return false
        }
        return true
    }

    private fun weightedFallback(context: PlacementContext, rosterVersion: Long, accepting: List<RosterHomeserver>): PlacementDecision? {
        val candidates = accepting.sortedBy { it.id }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { maxOf(0, it.weight) }
        val hash = stableHash(context, rosterVersion, candidates)
        if (totalWeight <= 0) return fallbackDecision(candidates[Math.floorMod(hash, candidates.size)])
        val target = Math.floorMod(hash, totalWeight)
        var cumulative = 0
        for (candidate in candidates) {
            cumulative += maxOf(0, candidate.weight)
            if (target < cumulative) return fallbackDecision(candidate)
        }
        return fallbackDecision(candidates.last())
    }

    private fun fallbackDecision(homeserver: RosterHomeserver) = PlacementDecision(homeserver.id, "WeightedFallbackRule", "weighted-fallback")
}
