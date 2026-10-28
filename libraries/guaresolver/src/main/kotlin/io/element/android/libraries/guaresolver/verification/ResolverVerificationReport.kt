/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

/** An unverifiable check is its own verdict: it is never counted as a pass. */
enum class Verdict { PASS, FAIL, UNVERIFIABLE }

sealed interface CheckOutcome {
    val code: String
    val verdict: Verdict
    val reason: String? get() = null
}

/** Which `gua-roster.v1` bytes the served roster is signed over; a mix of entry shapes has no bytes. */
enum class RosterForm { V1, LEGACY, MIXED }

sealed class RosterSignatureOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object Valid : RosterSignatureOutcome("valid", Verdict.PASS)
    data class Invalid(override val reason: String) : RosterSignatureOutcome("invalid", Verdict.FAIL)
    data class Unverifiable(override val reason: String) : RosterSignatureOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class HomeserverMatchOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object ActiveMatch : HomeserverMatchOutcome("ACTIVE_MATCH", Verdict.PASS)
    data object Inactive : HomeserverMatchOutcome("INACTIVE", Verdict.FAIL)
    data object FieldMismatch : HomeserverMatchOutcome("FIELD_MISMATCH", Verdict.FAIL)
    data object NotInRoster : HomeserverMatchOutcome("NOT_IN_ROSTER", Verdict.FAIL)
    data class Unverifiable(override val reason: String) : HomeserverMatchOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class MemberEntryOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object Valid : MemberEntryOutcome("valid", Verdict.PASS)
    data object Unattested : MemberEntryOutcome("unattested", Verdict.PASS)
    data class Invalid(override val reason: String) : MemberEntryOutcome("invalid", Verdict.FAIL)
    data class Unverifiable(override val reason: String) : MemberEntryOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class LogConsistencyOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object Consistent : LogConsistencyOutcome("consistent", Verdict.PASS)
    data object FirstSeen : LogConsistencyOutcome("firstSeen", Verdict.PASS)
    data object Rollback : LogConsistencyOutcome("rollback", Verdict.FAIL)
    data object Inconsistent : LogConsistencyOutcome("inconsistent", Verdict.FAIL)
    data class Unverifiable(override val reason: String) : LogConsistencyOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class PolicyOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object NotPresent : PolicyOutcome("notPresent", Verdict.PASS)
    data object Valid : PolicyOutcome("valid", Verdict.PASS)
    data class Invalid(override val reason: String) : PolicyOutcome("invalid", Verdict.FAIL)
    data class Unverifiable(override val reason: String) : PolicyOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class PlacementOutcome(override val code: String, override val verdict: Verdict) : CheckOutcome {
    data object Match : PlacementOutcome("match", Verdict.PASS)
    data object Mismatch : PlacementOutcome("mismatch", Verdict.FAIL)
    data object Indeterminate : PlacementOutcome("indeterminate", Verdict.UNVERIFIABLE)
    data object NotApplicable : PlacementOutcome("notApplicable", Verdict.PASS)
    data class Unverifiable(override val reason: String) : PlacementOutcome("unverifiable", Verdict.UNVERIFIABLE)
}

sealed class OverallOutcome(val code: String) {
    data object Verified : OverallOutcome("verified")
    data class Failed(val check: String) : OverallOutcome("failed")
    data class Unverifiable(val check: String) : OverallOutcome("unverifiable")
}

data class ResolverVerificationReport(
    val environment: String,
    val rosterVersion: Long?,
    val rosterForm: RosterForm?,
    val rosterSignature: RosterSignatureOutcome,
    val homeserverMatch: HomeserverMatchOutcome,
    val memberEntry: MemberEntryOutcome,
    val logConsistency: LogConsistencyOutcome,
    val policy: PolicyOutcome,
    val placement: PlacementOutcome,
    val durationMs: Long,
) {
    private val checks: List<Pair<String, CheckOutcome>>
        get() = listOf(
            "rosterSignature" to rosterSignature,
            "homeserverMatch" to homeserverMatch,
            "memberEntry" to memberEntry,
            "logConsistency" to logConsistency,
            "policy" to policy,
            "placement" to placement,
        )

    val overall: OverallOutcome
        get() {
            checks.firstOrNull { it.second.verdict == Verdict.FAIL }?.let { return OverallOutcome.Failed(it.first) }
            checks.firstOrNull { it.second.verdict == Verdict.UNVERIFIABLE }?.let { return OverallOutcome.Unverifiable(it.first) }
            return OverallOutcome.Verified
        }

    /** Outcome codes only: never the phone, the answer, the request or anything the server sent. */
    fun toLogLine(): String {
        val overallText = when (val summary = overall) {
            OverallOutcome.Verified -> summary.code
            is OverallOutcome.Failed -> "${summary.code}(${summary.check})"
            is OverallOutcome.Unverifiable -> "${summary.code}(${summary.check})"
        }
        val version = rosterVersion?.toString() ?: "none"
        val form = rosterForm?.name?.lowercase() ?: "none"
        return "Resolver verification: environment=$environment rosterVersion=$version rosterForm=$form " +
            checks.joinToString(" ") { (name, outcome) -> "$name=${outcome.describe()}" } +
            " overall=$overallText durationMs=$durationMs"
    }

    private fun CheckOutcome.describe(): String = reason?.let { "$code($it)" } ?: code
}
