/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import kotlinx.serialization.Serializable

/** The routing policy bundle as `GET /policy/routing` serves it. */
@Serializable
internal data class RoutingPolicyBundle(
    val schemaVersion: String? = null,
    val policyId: String? = null,
    val version: Long = 0,
    val issuedAt: String? = null,
    val notBefore: String? = null,
    val expiresAt: String? = null,
    val delegationZones: List<DelegationZone>? = null,
    val rules: List<RoutingPolicyRule>? = null,
    val fallback: FallbackStrategy? = null,
    val signatures: List<PolicySignature>? = null,
    val delegateSignatures: List<DelegateSignature>? = null,
)

@Serializable
internal data class DelegationZone(
    val id: String? = null,
    val scopeType: String? = null,
    val scopeValue: String? = null,
    val delegatedAuthority: String? = null,
    val delegateKeyId: String? = null,
    val delegatePublicKey: String? = null,
    val allowedHomeserverIds: List<String>? = null,
    val notBefore: String? = null,
    val expiresAt: String? = null,
) {
    /** A zone routes only inside its signed validity window. */
    fun isActiveAt(nowMillis: Long): Boolean {
        if (notBefore != null && notBefore.toEpochMillis() > nowMillis) return false
        return expiresAt == null || expiresAt.toEpochMillis() > nowMillis
    }
}

@Serializable
internal data class RoutingPolicyRule(
    val id: String? = null,
    val priority: Int = 0,
    val matchType: String? = null,
    val matchValue: String? = null,
    val oidcIssuer: String? = null,
    val oidcClaim: String? = null,
    val targetHomeserverId: String? = null,
    val delegatedZoneId: String? = null,
    val reason: String? = null,
    val assignmentPolicy: String? = null,
    val enabled: Boolean? = null,
) {
    val isEnabled: Boolean
        get() = enabled ?: true

    val effectiveAssignmentPolicy: String
        get() = assignmentPolicy ?: "PORTABLE"
}

@Serializable
internal data class FallbackStrategy(
    val type: String? = null,
    val enabled: Boolean = false,
)

@Serializable
internal data class PolicySignature(
    val authorityKeyId: String,
    val signatureB64: String,
)

@Serializable
internal data class DelegateSignature(
    val zoneId: String,
    val delegateKeyId: String,
    val signatureB64: String,
)

/**
 * The `gua-routing-policy.v1` bytes the policy keys sign and the `gua-delegated-rules.v1` bytes a zone's
 * delegate signs, ports of the resolver's `CanonicalRoutingPolicy` and `CanonicalDelegatedRules`.
 */
internal object CanonicalRoutingPolicy {
    private const val FIELD_SEPARATOR = "\u001f"

    fun bytes(bundle: RoutingPolicyBundle): ByteArray {
        val out = StringBuilder()
        out.append("gua-routing-policy.v1\n")
        out.append("schema=").append(bundle.schemaVersion.orEmpty()).append('\n')
        out.append("policyId=").append(bundle.policyId.orEmpty()).append('\n')
        out.append("version=").append(bundle.version).append('\n')
        out.append("issuedAt=").append(epoch(bundle.issuedAt)).append('\n')
        out.append("notBefore=").append(epoch(bundle.notBefore)).append('\n')
        out.append("expiresAt=").append(epoch(bundle.expiresAt)).append('\n')
        bundle.delegationZones.orEmpty()
            .sortedWith(compareBy(nullsLast<String>()) { it.id })
            .forEach { out.append(zoneLine(it)).append('\n') }
        sortedRules(bundle.rules).forEach { out.append(ruleLine(it)).append('\n') }
        val fallback = bundle.fallback
        out.append("fallback=").append(if (fallback == null) "" else fallback.type.orEmpty() + ":" + fallback.enabled).append('\n')
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    fun delegatedRulesBytes(policyId: String?, version: Long, zoneId: String, rules: List<RoutingPolicyRule>?): ByteArray {
        val out = StringBuilder()
        out.append("gua-delegated-rules.v1\n")
        out.append("policyId=").append(policyId.orEmpty()).append('\n')
        out.append("version=").append(version).append('\n')
        out.append("zoneId=").append(zoneId).append('\n')
        sortedRules(rules)
            .filter { it.delegatedZoneId == zoneId }
            .forEach { out.append(ruleLine(it)).append('\n') }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    /** Priority, then id with nulls last: the one rule order the authority and the delegates share. */
    fun sortedRules(rules: List<RoutingPolicyRule>?): List<RoutingPolicyRule> =
        rules.orEmpty().sortedWith(compareBy<RoutingPolicyRule> { it.priority }.thenBy(nullsLast<String>()) { it.id })

    private fun zoneLine(zone: DelegationZone): String = listOf(
        "zone",
        zone.id.orEmpty(),
        zone.scopeType.orEmpty(),
        zone.scopeValue.orEmpty(),
        zone.delegatedAuthority.orEmpty(),
        zone.delegateKeyId.orEmpty(),
        zone.delegatePublicKey.orEmpty(),
        zone.allowedHomeserverIds?.sorted()?.joinToString("|").orEmpty(),
        epoch(zone.notBefore).toString(),
        epoch(zone.expiresAt).toString(),
    ).joinToString(FIELD_SEPARATOR)

    private fun ruleLine(rule: RoutingPolicyRule): String = listOf(
        "rule",
        rule.id.orEmpty(),
        rule.priority.toString(),
        rule.matchType.orEmpty(),
        rule.matchValue.orEmpty(),
        rule.oidcIssuer.orEmpty(),
        rule.oidcClaim.orEmpty(),
        rule.targetHomeserverId.orEmpty(),
        rule.delegatedZoneId.orEmpty(),
        rule.reason.orEmpty(),
        rule.effectiveAssignmentPolicy,
        rule.isEnabled.toString(),
    ).joinToString(FIELD_SEPARATOR)

    private fun epoch(instant: String?): Long = instant?.toEpochMillis() ?: 0L
}

/**
 * Authority attestation of the whole bundle under the pinned policy keys, then per zone the delegate's
 * signature over the rules inside it under the delegate key the authority attested.
 */
internal class RoutingPolicyVerifier(
    private val policyKeys: List<TrustedKey>,
    private val threshold: Int,
) {
    fun check(bundle: RoutingPolicyBundle, canonical: ByteArray = CanonicalRoutingPolicy.bytes(bundle)): ThresholdCheck {
        val signatures = bundle.signatures.orEmpty().map { KeyedSignature(it.authorityKeyId, it.signatureB64) }
        return ThresholdCheck.count(policyKeys, threshold, canonical, signatures)
    }

    /** The zone ids whose rules carry a valid delegate signature; a rule applies only inside one of them. */
    fun delegateVerifiedZones(bundle: RoutingPolicyBundle): Set<String> {
        val signatures = bundle.delegateSignatures.orEmpty()
        return bundle.delegationZones.orEmpty()
            .filter { zone -> zone.isDelegateSigned(bundle, signatures) }
            .mapNotNullTo(mutableSetOf()) { it.id }
    }

    /** A malformed delegate key leaves the zone unverified: fail closed, as the resolver does. */
    private fun DelegationZone.isDelegateSigned(bundle: RoutingPolicyBundle, signatures: List<DelegateSignature>): Boolean {
        val zoneId = id ?: return false
        if (delegateKeyId.isNullOrBlank() || delegatePublicKey.isNullOrBlank()) return false
        val delegateKey = Ed25519Signatures.rawPublicKey(delegatePublicKey) ?: return false
        val canonical = CanonicalRoutingPolicy.delegatedRulesBytes(bundle.policyId, bundle.version, zoneId, bundle.rules)
        return signatures.any {
            it.zoneId == zoneId && it.delegateKeyId == delegateKeyId && Ed25519Signatures.verify(delegateKey, canonical, it.signatureB64)
        }
    }
}
