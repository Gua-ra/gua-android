/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption

import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
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
    // A hung network call must not leave the button spinning forever. This is deliberately NOT
    // NotYet: NotYet means "the client cannot read its own state", which is a quiet, expected
    // condition, whereas running past the ceiling means the repair actually failed. Collapsing the
    // two hid a real failure behind a branch whose whole contract is that nothing is wrong.
} ?: EncryptionRepairOutcome.Failed

/** What [repairWithoutReset] managed to do, so callers only offer a reset when one is warranted. */
sealed interface EncryptionRepairOutcome {
    /** Key storage is healthy. Nothing to show the user. */
    data object Repaired : EncryptionRepairOutcome

    /** Transient: the client cannot read its own state yet. Change nothing, do not offer a reset. */
    data object NotYet : EncryptionRepairOutcome

    /** Everything non-destructive has been tried. Only a reset can finish this device. */
    data object ResetRequired : EncryptionRepairOutcome

    /**
     * The repair ran past its ceiling, or failed outright.
     *
     * Deliberately distinct from [NotYet] and from [ResetRequired]. It is not NotYet, because
     * something did go wrong and the user is owed a message. It is not ResetRequired, because
     * nothing here established that a reset is needed, and a reset destroys the backup.
     */
    data object Failed : EncryptionRepairOutcome
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
    val enabled = enableRecovery(waitForBackupsToUpload = false)

    // Only the state can say whether that finished the job. Whether it is worth waiting for,
    // though, is settled by the call: recovery cannot flip to ENABLED off a call that failed, so
    // waiting after a failure is dead time the user spends on a spinner before the same answer.
    return if (awaitRecoveryEnabled(wait = enabled.isSuccess)) {
        EncryptionRepairOutcome.Repaired
    } else {
        EncryptionRepairOutcome.ResetRequired
    }
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
    // Best effort, and its Result is deliberately ignored. enableBackups fails on exactly the
    // accounts this exists to repair, because a backup version already exists on the server, and an
    // earlier version of this treated that failure as "try again later" and returned NotYet, which
    // does nothing at all: no navigation, no message, banner unchanged. That is what a user sees as
    // a button that does nothing.
    //
    // The state is the only honest verdict either way, so ask it.
    //
    // What the failure does settle is how long to wait for that state. Recovery only flips to
    // ENABLED off the back of a call that worked, so waiting after one that threw just holds the
    // user on a spinner before giving the same answer. Only wait when there is something to wait for.
    val enabled = enableBackups()

    return if (awaitRecoveryEnabled(wait = enabled.isSuccess)) {
        EncryptionRepairOutcome.Repaired
    } else {
        EncryptionRepairOutcome.ResetRequired
    }
}

/**
 * GUA FORK: provisions key storage straight after a reset, and does NOT go through
 * [repairWithoutReset].
 *
 * That path deliberately refuses [EncryptionService.enableRecovery] on an INCOMPLETE account,
 * because enabling rotates the secret store and would invalidate a recovery key saved elsewhere.
 * Immediately after a reset there is no such key left to protect and no cross-signing identity
 * either, so the conservative path can never succeed here.
 *
 * Two things have to be true for this to actually finish the device, and getting either wrong puts
 * the setup banner straight back in front of the user who has just completed a reset, where the
 * only thing it can offer them is another reset. That is the loop.
 *
 * First, wait for the identity. `Recovery::enable` exports whatever private cross-signing keys the
 * crypto store can hand over at the moment it runs, and the reset has only just uploaded them. Run
 * too early it exports nothing, writes a secret store holding only the backup key, and the account
 * drops straight back to INCOMPLETE.
 *
 * Second, judge by the state. enableRecovery reports success for a store it populated with nothing,
 * so its Result cannot be the verdict.
 */
suspend fun EncryptionService.provisionAfterReset(
    sessionVerificationService: SessionVerificationService,
): EncryptionRepairOutcome {
    sessionVerificationService.awaitVerifiedIdentity()

    // Rotating the secret store is normally the one thing to avoid, since it invalidates any
    // recovery key saved elsewhere for this account. Immediately after a reset there is no such key
    // and no earlier store left to strand, so a second attempt costs nothing.
    repeat(PROVISION_ATTEMPTS) {
        val enabled = enableRecovery(waitForBackupsToUpload = false)
        if (awaitRecoveryEnabled(wait = enabled.isSuccess)) return EncryptionRepairOutcome.Repaired
    }

    return EncryptionRepairOutcome.ResetRequired
}

/**
 * Waits until the session trusts its own cross-signing identity.
 *
 * This is the signal that the private keys the reset just minted have reached the crypto store and
 * can be exported into a secret store. Gives up quietly on the timeout: the caller checks the
 * resulting state either way, so a slow identity costs an attempt, not the whole provision.
 */
private suspend fun SessionVerificationService.awaitVerifiedIdentity(): Boolean {
    if (sessionVerifiedStatus.value == SessionVerifiedStatus.Verified) return true
    return withTimeoutOrNull(IDENTITY_TIMEOUT) {
        sessionVerifiedStatus.first { it == SessionVerifiedStatus.Verified }
        true
    } ?: false
}

/**
 * Gives the recovery state a moment to reflect a change we just made.
 *
 * [wait] is false when the call that would have caused the change failed. The current value is
 * still worth reading, because something else may have repaired the account concurrently, but
 * there is nothing on the way and holding the spinner open would only delay the same verdict.
 */
private suspend fun EncryptionService.awaitRecoveryEnabled(wait: Boolean): Boolean {
    if (recoveryStateStateFlow.value == RecoveryState.ENABLED) return true
    if (!wait) return false
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

// How long to let the reset's new cross-signing identity reach the crypto store before exporting it.
private val IDENTITY_TIMEOUT = 5.seconds

// One retry only. If a settled identity still will not export, retrying forever cannot help.
private const val PROVISION_ATTEMPTS = 2
