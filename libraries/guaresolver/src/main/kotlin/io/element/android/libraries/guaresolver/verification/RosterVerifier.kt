/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.core.data.tryOrNull

/** Authority signatures over a served roster, k of n, one vote per key, under the pinned authority keys. */
internal class RosterVerifier(
    private val authorityKeys: List<TrustedKey>,
    private val threshold: Int,
) {
    fun check(roster: SignedRoster, form: RosterForm): ThresholdCheck {
        val canonical = CanonicalRoster.bytes(roster, form)
        val signatures = roster.authoritySignatures.map { KeyedSignature(it.authorityKeyId, it.signatureB64) }
        return ThresholdCheck.count(authorityKeys, threshold, canonical, signatures)
    }

    fun verify(roster: SignedRoster, form: RosterForm): RosterSignatureOutcome {
        if (form == RosterForm.MIXED) return RosterSignatureOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM)
        val check = tryOrNull { check(roster, form) } ?: return RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
        return if (check.met) RosterSignatureOutcome.Valid else RosterSignatureOutcome.Invalid(check.failureReason)
    }

    companion object {
        /**
         * Where a `/resolve` answer sits in a verified roster, as the resolver's `ResolverVerifier.locate` puts it:
         * the strongest classification wins whatever the entry order, and a tie goes to the entry served first.
         */
        fun locate(serverName: String, baseUrl: String, masIssuer: String?, roster: SignedRoster): Located {
            val named = roster.entries.filter { it.homeserver.serverName == serverName }
            val sameEndpoint = named.filter { it.homeserver.baseUrl == baseUrl && it.homeserver.masIssuer == masIssuer }
            val active = sameEndpoint.firstOrNull { it.isActive }
            return when {
                active != null -> Located(HomeserverMatchOutcome.ActiveMatch, active)
                sameEndpoint.isNotEmpty() -> Located(HomeserverMatchOutcome.Inactive, sameEndpoint.first())
                named.isNotEmpty() -> Located(HomeserverMatchOutcome.FieldMismatch, named.first())
                else -> Located(HomeserverMatchOutcome.NotInRoster, entry = null)
            }
        }
    }
}

/** A homeserverMatch classification and the entry that decided it, null for NOT_IN_ROSTER. */
internal data class Located(val match: HomeserverMatchOutcome, val entry: SignedRosterEntry?)
