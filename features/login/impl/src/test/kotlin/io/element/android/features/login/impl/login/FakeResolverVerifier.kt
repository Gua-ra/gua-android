/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.verification.HomeserverMatchOutcome
import io.element.android.libraries.guaresolver.verification.LogConsistencyOutcome
import io.element.android.libraries.guaresolver.verification.MemberEntryOutcome
import io.element.android.libraries.guaresolver.verification.PlacementOutcome
import io.element.android.libraries.guaresolver.verification.PolicyOutcome
import io.element.android.libraries.guaresolver.verification.ResolverVerificationReport
import io.element.android.libraries.guaresolver.verification.ResolverVerifier
import io.element.android.libraries.guaresolver.verification.RosterForm
import io.element.android.libraries.guaresolver.verification.RosterSignatureOutcome

/** Records every call, so a test can assert which resolution the sign-in handed over, if any. */
class FakeResolverVerifier(
    private val verifyResult: suspend (String, HomeserverResolution) -> ResolverVerificationReport? = { _, _ -> aResolverVerificationReport() },
) : ResolverVerifier {
    val verifyCalls: MutableList<Pair<String, HomeserverResolution>> = mutableListOf()

    var completedCount: Int = 0
        private set

    override suspend fun verify(e164Phone: String, resolution: HomeserverResolution): ResolverVerificationReport? {
        verifyCalls += e164Phone to resolution
        return verifyResult(e164Phone, resolution).also { completedCount++ }
    }
}

fun aResolverVerificationReport() = ResolverVerificationReport(
    environment = "dev",
    rosterVersion = 7,
    rosterForm = RosterForm.V1,
    rosterSignature = RosterSignatureOutcome.Valid,
    homeserverMatch = HomeserverMatchOutcome.ActiveMatch,
    memberEntry = MemberEntryOutcome.Unattested,
    logConsistency = LogConsistencyOutcome.FirstSeen,
    policy = PolicyOutcome.NotPresent,
    placement = PlacementOutcome.Match,
    durationMs = 12,
)
