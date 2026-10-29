/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import io.element.android.libraries.core.data.tryOrNull

/** The policy as its POLICY_PUBLISH leaf names it: the leaf subject is `<policyId>:v<version>`, the payload its SHA-256. */
internal data class LoggedPolicy(val policyId: String?, val version: Long, val sha256Hex: String)

/**
 * The log step of the policy check: the served bundle, or a 404 when [served] is null, against the served log
 * events and the roster's signed checkpoint. Only the leaves before the checkpoint are signed.
 */
internal object PolicyLog {
    private const val POLICY_PUBLISH = "POLICY_PUBLISH"

    fun check(served: LoggedPolicy?, checkpoint: LogCheckpoint, events: List<LogEvent>): PolicyOutcome {
        if (checkpoint.size < 0 || checkpoint.size > events.size) return PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH)
        val signed = events.subList(0, checkpoint.size.toInt())
        val leaves = tryOrNull { signed.map { MerkleTree.leafHash(it.leafData().toByteArray(Charsets.UTF_8)) } }
            ?: return PolicyOutcome.Unverifiable(VerificationReason.PARSE_ERROR)
        if (MerkleTree.root(leaves) != checkpoint.merkleRoot) return PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH)
        val published = signed.filter { it.type == POLICY_PUBLISH }
        if (served == null) {
            // A policy the signed log publishes cannot be withheld behind a 404.
            return if (published.isEmpty()) PolicyOutcome.NotPresent else PolicyOutcome.Unverifiable(VerificationReason.POLICY_NOT_SERVED)
        }
        return when {
            published.any { it.payloadHash == served.sha256Hex } -> if (published.any { it.supersedes(served) }) {
                PolicyOutcome.Unverifiable(VerificationReason.POLICY_SUPERSEDED)
            } else {
                PolicyOutcome.Valid
            }
            // An honest publish is past the checkpoint until the roster is signed again.
            events.drop(signed.size).any { it.type == POLICY_PUBLISH && it.payloadHash == served.sha256Hex } ->
                PolicyOutcome.Unverifiable(VerificationReason.POLICY_NEWER_THAN_CHECKPOINT)
            else -> PolicyOutcome.Invalid(VerificationReason.POLICY_NOT_LOGGED)
        }
    }

    private fun LogEvent.supersedes(served: LoggedPolicy): Boolean {
        val subject = homeserverId ?: return false
        val split = subject.lastIndexOf(":v")
        if (split < 0) return false
        val version = subject.substring(split + 2).toLongOrNull() ?: return false
        return subject.substring(0, split) == served.policyId && version > served.version
    }
}
