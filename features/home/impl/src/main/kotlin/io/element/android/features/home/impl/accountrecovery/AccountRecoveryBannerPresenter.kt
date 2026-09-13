/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * GUA FORK: tells the owner, on every signed-in device, that someone has started a delayed account
 * recovery, and lets them cancel it.
 *
 * The status is read when the screen resumes, which covers the first start and every return to the
 * app, and again every [REFRESH_INTERVAL] while it stays resumed, or sooner when the recovery becomes
 * finishable or runs out before then, so the wording and the banner follow those moments. A failed
 * read changes nothing: it neither raises a warning without evidence nor takes down one that is up.
 */
@Inject
class AccountRecoveryBannerPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
    private val dateFormatter: DateFormatter,
    private val systemClock: SystemClock,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Presenter<AccountRecoveryBannerState> {
    @Composable
    override fun present(): AccountRecoveryBannerState {
        val coroutineScope = rememberCoroutineScope()
        var pendingRecovery by remember { mutableStateOf<PendingAccountRecovery?>(null) }
        var cancelAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        val statusReads = remember { StatusReads() }

        suspend fun refresh() = statusReads.mutex.withLock {
            val generation = statusReads.generation
            val status = fetchStatus() ?: return@withLock
            // A cancel went through while this read was out, so its answer may predate the cancel.
            // Dropping it keeps a read that fails after the cancel from leaving the banner up.
            if (generation != statusReads.generation) return@withLock
            statusReads.latest = status
            pendingRecovery = status.toPendingRecovery()
        }

        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose { isResumed = false }
        }
        // Keyed on the value this composition saw, not on a read inside the effect: the resume
        // callback can flip the state before the effect starts, and reading it there would start a
        // second loop alongside the one the recomposition starts.
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed) return@LaunchedEffect
            while (true) {
                refresh()
                delay(nextReadDelay(statusReads.latest))
            }
        }

        fun handleEvent(event: AccountRecoveryBannerEvent) {
            when (event) {
                AccountRecoveryBannerEvent.CancelRecovery -> if (cancelAction !is AsyncAction.Loading) {
                    cancelAction = AsyncAction.ConfirmingNoParams
                }
                AccountRecoveryBannerEvent.DismissCancelConfirmation -> if (cancelAction.isConfirming()) {
                    cancelAction = AsyncAction.Uninitialized
                }
                // Only from the confirmation dialog: a cancel is never sent without asking first.
                AccountRecoveryBannerEvent.ConfirmCancelRecovery -> if (cancelAction.isConfirming()) {
                    cancelAction = AsyncAction.Loading
                    coroutineScope.launch {
                        val result = accessToken()
                            ?.let { identityServiceClient.cancelAccountRecovery(it) }
                            ?: Result.failure(IllegalStateException("No access token for this session"))
                        result
                            .onSuccess {
                                // The server clears a live recovery on every successful cancel, so
                                // there is nothing left to warn about even if the read below fails.
                                statusReads.generation++
                                statusReads.latest = null
                                pendingRecovery = null
                                refresh()
                                snackbarDispatcher.post(SnackbarMessage(R.string.gua_account_recovery_cancelled))
                            }
                            .onFailure {
                                Timber.w(it, "Could not cancel the account recovery")
                                snackbarDispatcher.post(SnackbarMessage(R.string.gua_account_recovery_cancel_failed))
                            }
                        cancelAction = AsyncAction.Uninitialized
                    }
                }
            }
        }

        return AccountRecoveryBannerState(
            pendingRecovery = pendingRecovery,
            cancelAction = cancelAction,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun accessToken(): String? = sessionStore.getSession(matrixClient.sessionId.value)?.accessToken

    private suspend fun fetchStatus(): AccountFactorStatus? {
        val accessToken = accessToken() ?: return null
        return identityServiceClient.accountFactorStatus(accessToken, matrixClient.sessionId.value)
            .onFailure { Timber.w(it, "Could not read the account recovery status") }
            .getOrNull()
    }

    /**
     * [REFRESH_INTERVAL], or less when the live recovery in [status] becomes finishable or runs out
     * before then. Moments already past are ignored, so a server that still reports the recovery
     * after its expiry on this device's clock falls back to the periodic read.
     */
    private fun nextReadDelay(status: AccountFactorStatus?): Duration {
        if (status?.accountRecoveryPending != true) return REFRESH_INTERVAL
        val nowMillis = systemClock.epochMillis()
        val nextMomentMillis = listOfNotNull(
            status.accountRecoveryCompletableAtEpochSeconds,
            status.accountRecoveryExpiresAtEpochSeconds,
        )
            .map { it * MILLIS_PER_SECOND }
            .filter { it > nowMillis }
            .minOrNull()
            ?: return REFRESH_INTERVAL
        return minOf(REFRESH_INTERVAL, (nextMomentMillis - nowMillis).milliseconds + MOMENT_SLACK)
    }

    private fun AccountFactorStatus.toPendingRecovery(): PendingAccountRecovery? {
        if (!accountRecoveryPending) return null
        val completableAtMillis = accountRecoveryCompletableAtEpochSeconds?.times(MILLIS_PER_SECOND)
        return PendingAccountRecovery(
            finishableAfter = completableAtMillis
                ?.takeIf { it > systemClock.epochMillis() }
                ?.let { dateFormatter.format(it, DateFormatterMode.Full, useRelative = false) },
        )
    }

    /** The bookkeeping behind the reads. Only touched from the composition's coroutines. */
    private class StatusReads {
        /** One read at a time, so reads land in the order they started. */
        val mutex = Mutex()

        /** Moves on every successful cancel; a read that started before that is not applied. */
        var generation = 0

        /** The last status applied, which schedules the next read. */
        var latest: AccountFactorStatus? = null
    }

    companion object {
        val REFRESH_INTERVAL = 15.minutes

        /** Reads a moment later than the recovery's own times, so the server has passed them too. */
        private val MOMENT_SLACK = 1.seconds
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
