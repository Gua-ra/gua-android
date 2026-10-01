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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A failed read changes nothing: it neither raises the banner nor takes it down. */
@Inject
class AccountRecoveryBannerPresenter(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    private val identityServiceClient: IdentityServiceClient,
    private val systemClock: SystemClock,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Presenter<AccountRecoveryBannerState> {
    @Composable
    override fun present(): AccountRecoveryBannerState {
        val coroutineScope = rememberCoroutineScope()
        var pendingRecovery by remember { mutableStateOf<PendingAccountRecovery?>(null) }
        var cancelAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        val statusReads = remember { StatusReads() }

        suspend fun refresh(): Boolean = statusReads.mutex.withLock {
            val generation = statusReads.generation
            val status = fetchStatus() ?: return@withLock false
            // A cancel went through while this read was out, so its answer may predate the cancel.
            if (generation != statusReads.generation) return@withLock true
            statusReads.latest = status
            pendingRecovery = status.toPendingRecovery()
            true
        }

        var isResumed by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            isResumed = true
            onPauseOrDispose { isResumed = false }
        }
        // Keyed on the value this composition saw: reading the state inside the effect could start a second loop.
        val resumed = isResumed
        LaunchedEffect(resumed) {
            if (!resumed) return@LaunchedEffect
            var retrying = false
            while (true) {
                val failed = !refresh()
                // A failed read gets one early retry. Retries never chain.
                retrying = failed && !retrying
                val nextDelay = nextReadDelay(statusReads.latest)
                delay(if (retrying) minOf(RETRY_AFTER_FAILURE, nextDelay) else nextDelay)
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
                AccountRecoveryBannerEvent.ConfirmCancelRecovery -> if (cancelAction.isConfirming()) {
                    cancelAction = AsyncAction.Loading
                    coroutineScope.launch {
                        val result = accessToken()
                            ?.let { identityServiceClient.cancelAccountRecovery(it) }
                            ?: Result.failure(IllegalStateException("No access token for this session"))
                        result
                            .onSuccess {
                                // A successful cancel clears the live recovery even if the read below fails.
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

    /** [REFRESH_INTERVAL], or sooner when the recovery becomes finishable or expires before then. */
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
            ?: return PendingAccountRecovery.FinishableUnknown
        return if (completableAtMillis > systemClock.epochMillis()) {
            PendingAccountRecovery.FinishableFrom(longDate(completableAtMillis))
        } else {
            PendingAccountRecovery.FinishableNow
        }
    }

    /** Long date with the year, in the reader's zone. The shared DateFormatter cannot produce this shape. */
    private fun longDate(epochMillis: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private class StatusReads {
        val mutex = Mutex()

        var generation = 0

        var latest: AccountFactorStatus? = null
    }

    companion object {
        val REFRESH_INTERVAL = 15.minutes

        val RETRY_AFTER_FAILURE = 30.seconds

        /** Reads a moment later than the recovery's own times, so the server has passed them too. */
        private val MOMENT_SLACK = 1.seconds
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
