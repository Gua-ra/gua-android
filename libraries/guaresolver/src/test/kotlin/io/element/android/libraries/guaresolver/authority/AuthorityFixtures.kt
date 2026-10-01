/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import io.element.android.libraries.guaresolver.genesis.AccountId

val A_BOOTSTRAP_ACCOUNT_ID: String =
    AccountId.derive(AccountId.CLASS_BOOTSTRAP, "an adopting account".toByteArray()).value

const val A_HEAD_HASH_HEX: String = "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809"

const val A_PENDING_PREV_HASH_HEX: String = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"

const val AN_EMPTY_HEAD_HASH: String = "0000000000000000000000000000000000000000000000000000000000000000"

fun aBootstrapChain(
    accountId: String = A_BOOTSTRAP_ACCOUNT_ID,
    pending: AuthorityPendingTransition? = null,
): AuthorityChainState = AuthorityChainState(
    accountId = accountId,
    accountClass = "BOOTSTRAP",
    state = if (pending == null) {
        AuthorityChainState.STATE_BOOTSTRAP
    } else {
        AuthorityChainState.STATE_ADOPTION_PENDING
    },
    headSeq = 0,
    headHash = AN_EMPTY_HEAD_HASH,
    devices = emptyList(),
    pending = pending,
)

fun aRootedChain(
    devices: List<AuthorityDevice> = listOf(anAuthorityDevice()),
    headSeq: Long = 1,
    pending: AuthorityPendingTransition? = null,
): AuthorityChainState = AuthorityChainState(
    accountId = A_BOOTSTRAP_ACCOUNT_ID,
    accountClass = "BOOTSTRAP",
    state = AuthorityChainState.STATE_ROOTED,
    headSeq = headSeq,
    headHash = A_HEAD_HASH_HEX,
    devices = devices,
    pending = pending,
)

fun anAuthorityDevice(
    deviceKeyB64Url: String = "ZGV2aWNlLWtleQ",
    label: String = "Pixel 9",
    state: String = "ACTIVE",
    quarantineUntilEpochSeconds: Long? = null,
    grantedSeq: Long = 1,
): AuthorityDevice = AuthorityDevice(
    deviceKeyB64Url = deviceKeyB64Url,
    label = label,
    state = state,
    quarantineUntilEpochSeconds = quarantineUntilEpochSeconds,
    grantedSeq = grantedSeq,
)

fun aPendingAdoption(
    effectiveAtEpochSeconds: Long = 1_800_000_000,
    recordHash: String = "a-record-hash",
): AuthorityPendingTransition = AuthorityPendingTransition(
    type = "ADOPT_ROOT",
    seq = 1,
    effectiveAtEpochSeconds = effectiveAtEpochSeconds,
    recordHash = recordHash,
)

fun aSubmission(
    seq: Long = 1,
    state: String = "PENDING",
    effectiveAtEpochSeconds: Long = 1_800_000_000,
    recordHash: String = "a-record-hash",
): AuthoritySubmission = AuthoritySubmission(
    seq = seq,
    state = state,
    effectiveAtEpochSeconds = effectiveAtEpochSeconds,
    recordHash = recordHash,
)

fun anApproval(
    approvalId: String = "AAECAwQFBgcICQoLDA0ODw",
    code: String = "AB7K",
    action: String? = "add-recovery-contact",
    actionDigestB64Url: String = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
    challengeB64Url: String = FakeAccountAuthorityClient.A_CHALLENGE_B64,
    expiresAtEpochSeconds: Long = 1_800_000_600,
): AuthorityApproval = AuthorityApproval(
    approvalId = approvalId,
    code = code,
    action = action,
    actionDigestB64Url = actionDigestB64Url,
    challengeB64Url = challengeB64Url,
    expiresAtEpochSeconds = expiresAtEpochSeconds,
)
