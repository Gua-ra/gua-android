/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import java.nio.ByteBuffer
import java.security.MessageDigest

/** A public caller has no verified claims, so its affiliations and attributes never count. */
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
    fun withoutUnverifiedClaims(): PlacementContext = if (claimsVerified) this else copy(affiliations = emptyList(), attributes = emptyMap())

    /** Not injective and not signed: it only seeds the weighted fallback's hash. */
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
    val homeserverId: String?,
    val rule: String,
    val ruleId: String,
)

/** Port of the resolver's new-account placement: `PolicyRoutingRule`, then `ClaimRule`, then `WeightedFallbackRule`. */
internal object PlacementEngine {
    fun decide(
        context: PlacementContext,
        roster: SignedRoster,
        policy: RoutingPolicyBundle?,
        delegateVerifiedZones: Set<String>,
        nowMillis: Long,
    ): PlacementDecision? {
        val placed = context.withoutUnverifiedClaims()
        val accepting = roster.entries.filter { it.isActive && it.homeserver.acceptsNew }
        return policy?.let { policyRule(placed, it, delegateVerifiedZones, accepting, nowMillis) }
            ?: claimRule(placed, accepting)
            ?: weightedFallback(placed, roster.version, accepting.map { it.homeserver })
    }

    /**
     * Neither the bundle signatures nor its log leaf cover delegate signatures, so a resolver can strip one to hide a
     * rule. Every policy rule ranks above roster claims and the fallback.
     */
    fun unsignedRuleOutranks(
        context: PlacementContext,
        policy: RoutingPolicyBundle,
        delegateVerifiedZones: Set<String>,
        decision: PlacementDecision?,
        nowMillis: Long,
    ): Boolean {
        val placed = context.withoutUnverifiedClaims()
        val zones = zonesById(policy)
        val rules = CanonicalRoutingPolicy.sortedRules(policy.rules)
        val decided = decision?.takeIf { it.rule == POLICY_RULE }?.let { d -> rules.firstOrNull { it.id.orEmpty() == d.ruleId } }
        return rules
            .filter { it.isEnabled && it.delegatedZoneId !in delegateVerifiedZones && it.zoneInEffect(zones, nowMillis) && it.matches(placed) }
            .any { decided == null || CanonicalRoutingPolicy.ruleOrder.compare(it, decided) < 0 }
    }

    fun stableHash(context: PlacementContext, rosterVersion: Long, candidates: List<RosterHomeserver>): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("weighted-fallback.v1\nroster=$rosterVersion\n".toByteArray(Charsets.UTF_8))
        digest.update(context.routingKey().toByteArray(Charsets.UTF_8))
        for (candidate in candidates) {
            digest.update("\n${candidate.id ?: "null"}:${maxOf(0, candidate.weight)}".toByteArray(Charsets.UTF_8))
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
        val zones = zonesById(policy)
        return CanonicalRoutingPolicy.sortedRules(policy.rules)
            .filter { it.isEnabled && it.delegatedZoneId in delegateVerifiedZones && it.zoneInEffect(zones, nowMillis) && it.matches(context) }
            .firstNotNullOfOrNull { rule ->
                rule.targetHomeserverId?.let { targets[it] }?.let { PlacementDecision(it.homeserver.id, POLICY_RULE, rule.id.orEmpty()) }
            }
    }

    // The first zone with an id wins a duplicate, as the resolver's merge function keeps it.
    private fun zonesById(policy: RoutingPolicyBundle): Map<String, DelegationZone> =
        policy.delegationZones.orEmpty().mapNotNull { zone -> zone.id?.let { it to zone } }.distinctBy { it.first }.toMap()

    private fun RoutingPolicyRule.zoneInEffect(zones: Map<String, DelegationZone>, nowMillis: Long): Boolean =
        delegatedZoneId?.let { zones[it] }?.isInEffectAt(nowMillis) == true

    private fun RoutingPolicyRule.matches(context: PlacementContext): Boolean = when (matchType) {
        "PHONE_PREFIX" -> matchValue != null && context.e164Phone?.startsWith(matchValue) == true
        // Institution and OIDC rules need signature-verified claims, which a phone-only context never has.
        else -> false
    }

    private fun claimRule(context: PlacementContext, accepting: List<SignedRosterEntry>): PlacementDecision? =
        accepting
            .flatMap { entry -> entry.claims.orEmpty().filter { !it.isRemote && it.matchesLocally(context) }.map { entry to it } }
            .minWithOrNull(compareBy<Pair<SignedRosterEntry, RosterClaim>> { it.second.priority }.thenBy { it.first.homeserver.id })
            ?.let { (entry, claim) -> PlacementDecision(entry.homeserver.id, "ClaimRule", "roster-claim:${entry.homeserver.id ?: "null"}:${claim.priority}") }

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

    /** Weights are summed in 64 bits, as the resolver does, so a total past 2^31 does not wrap. */
    private fun weightedFallback(context: PlacementContext, rosterVersion: Long, accepting: List<RosterHomeserver>): PlacementDecision? {
        val candidates = accepting.sortedBy { it.id }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { maxOf(0, it.weight).toLong() }
        val hash = stableHash(context, rosterVersion, candidates)
        if (totalWeight == 0L) return fallbackDecision(candidates[Math.floorMod(hash, candidates.size)])
        val target = Math.floorMod(hash.toLong(), totalWeight)
        var cumulative = 0L
        for (candidate in candidates) {
            cumulative += maxOf(0, candidate.weight)
            if (target < cumulative) return fallbackDecision(candidate)
        }
        return fallbackDecision(candidates.last())
    }

    private fun fallbackDecision(homeserver: RosterHomeserver) = PlacementDecision(homeserver.id, "WeightedFallbackRule", "weighted-fallback")

    private const val POLICY_RULE = "PolicyRoutingRule"
}
