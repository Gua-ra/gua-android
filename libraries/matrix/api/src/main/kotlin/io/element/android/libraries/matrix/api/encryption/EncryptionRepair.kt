/*
 * Copyright Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * GUA FORK: repair key storage using only the paths that cannot destroy anything.
 *
 * Gua never shows anyone a recovery key, so a device that lands in [RecoveryState.INCOMPLETE]
 * has no way back on its own. Everything here is non-destructive: it either finishes provisioning
 * key storage or it fails, and it never discards the server-side backup. Callers that must offer
 * a reset only do so after this has failed, so the destructive step is disclosed at the point it
 * genuinely becomes necessary rather than up front.
 */
suspend fun EncryptionService.repairWithoutReset(): Result<Unit> {
    return when (val state = settledRecoveryState()) {
        RecoveryState.ENABLED -> Result.success(Unit)
        // Still bootstrapping after the wait: acting now could reset an account that was
        // actually fine, so refuse rather than guess.
        RecoveryState.UNKNOWN, RecoveryState.WAITING_FOR_SYNC -> Result.failure(EncryptionRepairFailed(state))
        RecoveryState.DISABLED -> enableRecovery(waitForBackupsToUpload = false).map { }
        RecoveryState.INCOMPLETE -> {
            // Another signed-in device may still hand the secrets over, which repairs this for
            // free and keeps the backup. Wait for that rather than testing whether such a device
            // exists: the device list is full of stale entries that will never respond, so the
            // existence check reports "yes" forever and nothing is ever repaired.
            if (awaitRecoveryEnabled(REPAIR_TIMEOUT)) {
                Result.success(Unit)
            } else {
                // Fails when a backup already exists on the server. That is the one case the
                // caller has to escalate, and it is exactly when a reset becomes unavoidable.
                enableRecovery(waitForBackupsToUpload = false).map { }
            }
        }
    }
}

/** Waits out the transient bootstrapping states so callers never branch on a value that is about to change. */
suspend fun EncryptionService.settledRecoveryState(timeout: Duration = SETTLE_TIMEOUT): RecoveryState {
    return withTimeoutOrNull(timeout) {
        recoveryStateStateFlow.first { it != RecoveryState.WAITING_FOR_SYNC && it != RecoveryState.UNKNOWN }
    } ?: recoveryStateStateFlow.value
}

private suspend fun EncryptionService.awaitRecoveryEnabled(timeout: Duration): Boolean {
    return withTimeoutOrNull(timeout) {
        recoveryStateStateFlow.first { it == RecoveryState.ENABLED }
        true
    } ?: false
}

class EncryptionRepairFailed(state: RecoveryState) : Exception("Key storage is not repairable from state $state")

private val SETTLE_TIMEOUT = 10.seconds
private val REPAIR_TIMEOUT = 10.seconds
