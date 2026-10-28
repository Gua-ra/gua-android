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

    fun verify(roster: SignedRoster, form: RosterForm = CanonicalRoster.formOf(roster)): RosterSignatureOutcome {
        if (form == RosterForm.MIXED) return RosterSignatureOutcome.Unverifiable(VerificationReason.MIXED_ROSTER_FORM)
        val check = tryOrNull { check(roster, form) } ?: return RosterSignatureOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
        return if (check.met) RosterSignatureOutcome.Valid else RosterSignatureOutcome.Invalid(check.failureReason)
    }

    companion object {
        /**
         * How a `/resolve` answer relates to a verified roster. Only an active entry with exactly these fields
         * may be connected to: a known server name with another base URL or issuer is the shape of a redirect.
         */
        fun locate(serverName: String, baseUrl: String, masIssuer: String?, roster: SignedRoster): HomeserverMatchOutcome {
            var best: HomeserverMatchOutcome = HomeserverMatchOutcome.NotInRoster
            for (entry in roster.entries) {
                val homeserver = entry.homeserver
                if (homeserver.serverName != serverName) continue
                val sameEndpoint = homeserver.baseUrl == baseUrl && homeserver.masIssuer == masIssuer
                if (sameEndpoint && entry.isActive) return HomeserverMatchOutcome.ActiveMatch
                best = if (sameEndpoint) HomeserverMatchOutcome.Inactive else HomeserverMatchOutcome.FieldMismatch
            }
            return best
        }
    }
}
