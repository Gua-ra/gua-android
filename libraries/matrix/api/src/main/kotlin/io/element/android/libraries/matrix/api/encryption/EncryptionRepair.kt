/*
 * Copyright 2026 Gua
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
suspend fun EncryptionService.repairWithoutReset(): EncryptionRepairOutcome = withTimeoutOrNull(REPAIR_CEILING) {
    when (settledRecoveryState()) {
        RecoveryState.ENABLED -> EncryptionRepairOutcome.Repaired
        // Not a broken account, just a client that cannot see its own state yet: on Android the
        // recovery flow is pinned to WAITING_FOR_SYNC the whole time sync is not Running, so this
        // is what a tap during a network blip looks like. Treating it as "needs a reset" would
        // march the user into a MAS round trip to fix nothing. Leave the banner and let them retry.
        RecoveryState.UNKNOWN, RecoveryState.WAITING_FOR_SYNC -> EncryptionRepairOutcome.NotYet
        // GUA FORK: DISABLED and INCOMPLETE take the same path now, and it does not wait around.
        //
        // It used to sit for up to ten seconds hoping another signed-in device would gossip the
        // secrets across, on top of a ten second settle. That is twenty seconds of a button that
        // looks broken, to buy a rescue that never arrives for the accounts this exists to fix:
        // their other device entries are stale reinstalls that answer nothing. Provision instead,
        // and escalate past a backup nobody can decrypt any more.
        // Nothing provisioned yet, so there is no secret store to damage.
        RecoveryState.DISABLED -> provisionKeyStorage()
        RecoveryState.INCOMPLETE -> repairIncomplete()
    }
    // A hung network call must not leave the button spinning forever.
} ?: EncryptionRepairOutcome.NotYet

/** What [repairWithoutReset] managed to do, so callers only offer a reset when one is warranted. */
sealed interface EncryptionRepairOutcome {
    /** Key storage is healthy. Nothing to show the user. */
    data object Repaired : EncryptionRepairOutcome

    /** Transient: the client cannot read its own state yet. Change nothing, do not offer a reset. */
    data object NotYet : EncryptionRepairOutcome

    /** Everything non-destructive has been tried. Only a reset can finish this device. */
    data object ResetRequired : EncryptionRepairOutcome
}

/**
 * Provisions key storage, and reports honestly whether it actually worked.
 *
 * [enableRecovery] SUCCEEDS on a device that holds no private cross-signing keys: it mints a new
 * secret store, exports nothing into it, and the account falls straight back to INCOMPLETE. Taking
 * that success at face value is why the button appeared to do nothing at all, so the state itself
 * is the verdict here, not the call's return value.
 *
 * There is deliberately no disableRecovery() escalation. It cannot help and it can permanently
 * harm: Backups.disable() throws BackupNotEnabled exactly when the local store has no backup
 * version, which is the precise condition under which enableRecovery returns BackupExistsOnServer,
 * so the two are exact complements and the escalation can never fire. In the one case it would
 * fire, it blanks the m.cross_signing.* account data, destroying the last server-side copy of the
 * private cross-signing keys and forcing a reset on that account forever after.
 */
private suspend fun EncryptionService.provisionKeyStorage(): EncryptionRepairOutcome {
    enableRecovery(waitForBackupsToUpload = false)

    // Only the state can say whether that finished the job.
    return if (awaitRecoveryEnabled()) EncryptionRepairOutcome.Repaired else EncryptionRepairOutcome.ResetRequired
}

/**
 * Repairs an account whose secret storage exists but whose secrets this device cannot use.
 *
 * Deliberately does NOT call [enableRecovery]. `Recovery::enable` always runs `create_secret_store`,
 * which mints a new SSSS key and PUTs a new `m.secret_storage.default_key`. That leaves the previous
 * store's `m.cross_signing.*` copies stranded and permanently invalidates any recovery key already
 * saved for this account, including one sitting in the same user's iOS keychain. On a device with no
 * private cross-signing keys it cannot help anyway, because there is nothing to export into the new
 * store. So the tap would spend the account's last silent way back and still end at a reset.
 *
 * Turning backups on is the one non-rotating thing worth trying: it costs nothing when it fails.
 */
private suspend fun EncryptionService.repairIncomplete(): EncryptionRepairOutcome {
    enableBackups()
    return if (awaitRecoveryEnabled()) EncryptionRepairOutcome.Repaired else EncryptionRepairOutcome.ResetRequired
}

/** Gives the recovery state a moment to reflect a change we just made. */
private suspend fun EncryptionService.awaitRecoveryEnabled(): Boolean {
    if (recoveryStateStateFlow.value == RecoveryState.ENABLED) return true
    return withTimeoutOrNull(CONFIRM_TIMEOUT) {
        recoveryStateStateFlow.first { it == RecoveryState.ENABLED }
        true
    } ?: false
}

/** Waits out the transient bootstrapping states so callers never branch on a value that is about to change. */
suspend fun EncryptionService.settledRecoveryState(timeout: Duration = SETTLE_TIMEOUT): RecoveryState {
    return withTimeoutOrNull(timeout) {
        recoveryStateStateFlow.first { it != RecoveryState.WAITING_FOR_SYNC && it != RecoveryState.UNKNOWN }
    } ?: recoveryStateStateFlow.value
}

// Only long enough to leave the transient bootstrapping states. The flow is populated on the first
// sync, which has long since happened by the time a banner is on screen and someone taps it, so in
// practice this returns immediately and the cap only matters when sync is genuinely stalled.
private val SETTLE_TIMEOUT = 2.seconds

// enableRecovery has already returned by this point; this only covers the flow catching up.
private val CONFIRM_TIMEOUT = 2.seconds

// Hard ceiling on the whole operation, so the banner's spinner always resolves.
private val REPAIR_CEILING = 12.seconds
