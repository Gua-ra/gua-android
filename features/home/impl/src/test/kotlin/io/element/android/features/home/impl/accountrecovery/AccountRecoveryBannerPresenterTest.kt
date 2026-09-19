/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.R
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.FakeLifecycleOwner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.testWithLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AccountRecoveryBannerPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val defaultLocale = Locale.getDefault()
    private val defaultTimeZone = TimeZone.getDefault()

    // The banner formats the date in the reader's own locale and zone, so both are pinned here:
    // the assertions below are on the real formatted string, not on a fake formatter's echo.
    @Before
    fun pinLocaleAndZone() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreLocaleAndZone() {
        Locale.setDefault(defaultLocale)
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun `nothing is read before the screen resumes, and a live recovery then shows with its date`() = runTest {
        val statusReads = mutableListOf<Pair<String, String>>()
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { token, userId ->
                    statusReads += token to userId
                    Result.success(aRecoveryStatus(pending = true))
                },
            ),
        )
        val lifecycleOwner = FakeLifecycleOwner()
        presenter.testWithLifecycleOwner(lifecycleOwner) {
            val initialState = awaitItem()
            assertThat(initialState.pendingRecovery).isNull()
            assertThat(initialState.cancelAction).isEqualTo(AsyncAction.Uninitialized)
            runCurrent()
            assertThat(statusReads).isEmpty()

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            // A localised long date with the year, and never a time of day: the server's instant is
            // 2026-09-21T14:13:20Z, and only the date it falls on reaches the banner.
            assertThat(shownState.pendingRecovery).isEqualTo(
                PendingAccountRecovery.FinishableFrom("September 21, 2026")
            )
            assertThat(statusReads).containsExactly(AN_ACCESS_TOKEN to A_SESSION_ID.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a recovery that can already be finished says so instead of giving a time`() = runTest {
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.success(aRecoveryStatus(pending = true)) },
            ),
            systemClock = FakeSystemClock(epochMillisResult = A_COMPLETABLE_AT_EPOCH_SECONDS * 1000),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            assertThat(shownState.pendingRecovery).isEqualTo(PendingAccountRecovery.FinishableNow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a live recovery the server gave no completable moment claims nothing about timing`() = runTest {
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                // What an identity service too old to publish the field answers, which the client
                // decodes as null rather than refusing.
                accountFactorStatusResult = { _, _ ->
                    Result.success(aRecoveryStatus(pending = true, completableAtEpochSeconds = null))
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            // Not FinishableNow: nothing said the moment had passed, and saying so would tell the
            // owner the time they have to cancel is already gone.
            assertThat(shownState.pendingRecovery).isEqualTo(PendingAccountRecovery.FinishableUnknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no live recovery keeps the banner hidden`() = runTest {
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(aRecoveryStatus(pending = false))
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(statusReads).isEqualTo(1)
            assertThat(expectMostRecentItem().pendingRecovery).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the status is read again on every resume`() = runTest {
        val results = ArrayDeque(listOf(aRecoveryStatus(pending = true), aRecoveryStatus(pending = false)))
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(results.removeFirst())
                },
            ),
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        presenter.testWithLifecycleOwner(lifecycleOwner) {
            consumeItemsUntilPredicate { it.pendingRecovery != null }
            assertThat(statusReads).isEqualTo(1)

            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            runCurrent()
            assertThat(statusReads).isEqualTo(1)

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            val hiddenState = consumeItemsUntilPredicate { it.pendingRecovery == null }.last()
            assertThat(hiddenState.pendingRecovery).isNull()
            assertThat(statusReads).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the status is read again every 15 minutes while resumed, and not while paused`() = runTest {
        val results = ArrayDeque(listOf(aRecoveryStatus(pending = false), aRecoveryStatus(pending = true)))
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(results.removeFirstOrNull() ?: aRecoveryStatus(pending = true))
                },
            ),
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        presenter.testWithLifecycleOwner(lifecycleOwner) {
            runCurrent()
            assertThat(statusReads).isEqualTo(1)
            assertThat(expectMostRecentItem().pendingRecovery).isNull()

            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(1)

            advanceTimeBy(1.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            assertThat(consumeItemsUntilPredicate { it.pendingRecovery != null }.last().pendingRecovery).isNotNull()

            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            runCurrent()
            advanceTimeBy(45.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the status is read again when the recovery becomes finishable and when it runs out`() = runTest {
        val startMillis = (A_COMPLETABLE_AT_EPOCH_SECONDS - 5 * 60) * 1000
        val liveRecovery = aRecoveryStatus(pending = true)
            .copy(accountRecoveryExpiresAtEpochSeconds = A_COMPLETABLE_AT_EPOCH_SECONDS + 5 * 60)
        val results = ArrayDeque(listOf(liveRecovery, liveRecovery, aRecoveryStatus(pending = false)))
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(results.removeFirst())
                },
            ),
            // Follows the test's virtual time, so the wall clock and the scheduled reads agree.
            systemClock = object : SystemClock {
                override fun epochMillis() = startMillis + testScheduler.currentTime
            },
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val waitingState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            assertThat(waitingState.pendingRecovery).isInstanceOf(PendingAccountRecovery.FinishableFrom::class.java)
            assertThat(statusReads).isEqualTo(1)

            advanceTimeBy(5.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(1)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            val finishableState = consumeItemsUntilPredicate { it.pendingRecovery == PendingAccountRecovery.FinishableNow }.last()
            assertThat(finishableState.pendingRecovery).isEqualTo(PendingAccountRecovery.FinishableNow)

            advanceTimeBy(5.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)
            assertThat(consumeItemsUntilPredicate { it.pendingRecovery == null }.last().pendingRecovery).isNull()

            // Nothing is left to wait for, so the next read is the periodic one.
            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed read neither hides a warning that is up nor raises one`() = runTest {
        val results = ArrayDeque<Result<AccountFactorStatus>>(
            listOf(Result.success(aRecoveryStatus(pending = true)), Result.failure(ResolverError.Server(503)))
        )
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    results.removeFirst()
                },
            ),
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        presenter.testWithLifecycleOwner(lifecycleOwner) {
            consumeItemsUntilPredicate { it.pendingRecovery != null }
            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            val state = expectMostRecentItem()
            assertThat(state.pendingRecovery).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed first read shows nothing`() = runTest {
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.failure(ResolverError.Server(500)) },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(expectMostRecentItem().pendingRecovery).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed read is tried again 30 seconds later, with the token the session holds by then`() = runTest {
        val statusReads = mutableListOf<String>()
        val sessionStore = InMemorySessionStore(
            listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = AN_ACCESS_TOKEN)),
        )
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { token, _ ->
                    statusReads += token
                    // The token expired while the app was in the background.
                    if (token == AN_ACCESS_TOKEN) {
                        Result.failure(ResolverError.Server(401))
                    } else {
                        Result.success(aRecoveryStatus(pending = true))
                    }
                },
            ),
            sessionStore = sessionStore,
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(statusReads).containsExactly(AN_ACCESS_TOKEN)
            assertThat(expectMostRecentItem().pendingRecovery).isNull()
            sessionStore.updateData(aSessionData(sessionId = A_SESSION_ID.value, accessToken = A_REFRESHED_ACCESS_TOKEN))

            advanceTimeBy(29.seconds)
            runCurrent()
            assertThat(statusReads).hasSize(1)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertThat(statusReads).containsExactly(AN_ACCESS_TOKEN, A_REFRESHED_ACCESS_TOKEN).inOrder()
            assertThat(consumeItemsUntilPredicate { it.pendingRecovery != null }.last().pendingRecovery).isNotNull()

            // The retry read fine, so the next read is the periodic one.
            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).hasSize(2)
            advanceTimeBy(1.minutes)
            runCurrent()
            assertThat(statusReads).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a retry that fails too waits for the periodic read, and the next failure gets its own retry`() = runTest {
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.failure(ResolverError.Server(503))
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(statusReads).isEqualTo(1)

            advanceTimeBy(30.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)

            // No retry of the retry.
            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            advanceTimeBy(1.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)

            advanceTimeBy(30.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(4)
            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(4)
            assertThat(expectMostRecentItem().pendingRecovery).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a resume while a retry is waiting replaces it instead of adding a second one`() = runTest {
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.failure(ResolverError.Server(401))
                },
            ),
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        presenter.testWithLifecycleOwner(lifecycleOwner) {
            runCurrent()
            assertThat(statusReads).isEqualTo(1)

            advanceTimeBy(10.seconds)
            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            runCurrent()
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)

            // The retry of the first read is gone; only the resume read's own retry is left.
            advanceTimeBy(20.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            advanceTimeBy(10.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)
            advanceTimeBy(14.minutes)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a retry does not wait past a moment of the recovery that comes sooner`() = runTest {
        val startMillis = A_COMPLETABLE_AT_EPOCH_SECONDS * 1000
        // Finishable 20 seconds in, and runs out 40 seconds in.
        val liveRecovery = aRecoveryStatus(pending = true).copy(
            accountRecoveryCompletableAtEpochSeconds = A_COMPLETABLE_AT_EPOCH_SECONDS + 20,
            accountRecoveryExpiresAtEpochSeconds = A_COMPLETABLE_AT_EPOCH_SECONDS + 40,
        )
        val results = ArrayDeque<Result<AccountFactorStatus>>(
            listOf(
                Result.success(liveRecovery),
                Result.failure(ResolverError.Server(503)),
                Result.success(aRecoveryStatus(pending = false)),
            )
        )
        var statusReads = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    results.removeFirst()
                },
            ),
            systemClock = object : SystemClock {
                override fun epochMillis() = startMillis + testScheduler.currentTime
            },
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            consumeItemsUntilPredicate { it.pendingRecovery != null }
            assertThat(statusReads).isEqualTo(1)

            // The read at the finishable moment fails, and the warning stays up.
            advanceTimeBy(21.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            // Nothing moves, so the warning that is up stays up.
            expectNoEvents()

            // Its retry comes at the expiry, 20 seconds later, not 30.
            advanceTimeBy(19.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(2)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertThat(statusReads).isEqualTo(3)
            assertThat(consumeItemsUntilPredicate { it.pendingRecovery == null }.last().pendingRecovery).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without an access token nothing is sent and nothing is shown`() = runTest {
        val presenter = createAccountRecoveryBannerPresenter(
            sessionStore = InMemorySessionStore(),
            // The default fake fails the test on any call.
            identityServiceClient = FakeIdentityServiceClient(),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(expectMostRecentItem().pendingRecovery).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel asks first, then cancels, reads the status again and confirms`() = runTest {
        val results = ArrayDeque(listOf(aRecoveryStatus(pending = true), aRecoveryStatus(pending = false)))
        var statusReads = 0
        val cancelCalls = mutableListOf<String>()
        val snackbarDispatcher = SnackbarDispatcher()
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(results.removeFirst())
                },
                cancelAccountRecoveryResult = { token ->
                    cancelCalls += token
                    Result.success(Unit)
                },
            ),
            snackbarDispatcher = snackbarDispatcher,
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()

            shownState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            val confirmingState = consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last()
            assertThat(cancelCalls).isEmpty()

            confirmingState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            val doneState = consumeItemsUntilPredicate {
                it.pendingRecovery == null && it.cancelAction == AsyncAction.Uninitialized
            }.last()
            assertThat(doneState.pendingRecovery).isNull()
            assertThat(cancelCalls).containsExactly(AN_ACCESS_TOKEN)
            assertThat(statusReads).isEqualTo(2)
            assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId).isEqualTo(R.string.gua_account_recovery_cancelled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the cancel button shows progress while the request runs, and a second press sends nothing`() = runTest {
        val cancelResponse = CompletableDeferred<Result<Unit>>()
        var cancelCalls = 0
        var cancelled = false
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.success(aRecoveryStatus(pending = !cancelled)) },
                cancelAccountRecoveryResult = {
                    cancelCalls++
                    cancelResponse.await().also { cancelled = true }
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            shownState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            val confirmingState = consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last()
            confirmingState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            val loadingState = consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Loading }.last()
            loadingState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            loadingState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            runCurrent()
            assertThat(cancelCalls).isEqualTo(1)

            cancelResponse.complete(Result.success(Unit))
            val doneState = consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Uninitialized }.last()
            assertThat(doneState.pendingRecovery).isNull()
            assertThat(cancelCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a read that was out when the cancel went through cannot put the warning back`() = runTest {
        val staleRead = CompletableDeferred<Result<AccountFactorStatus>>()
        val reads = ArrayDeque<suspend () -> Result<AccountFactorStatus>>(
            listOf(
                { Result.success(aRecoveryStatus(pending = true)) },
                { staleRead.await() },
                { Result.failure(ResolverError.Server(503)) },
            )
        )
        val snackbarDispatcher = SnackbarDispatcher()
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> reads.removeFirst().invoke() },
                cancelAccountRecoveryResult = { Result.success(Unit) },
            ),
            snackbarDispatcher = snackbarDispatcher,
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            // The periodic read goes out and has not answered yet.
            advanceTimeBy(AccountRecoveryBannerPresenter.REFRESH_INTERVAL)
            runCurrent()
            assertThat(reads).hasSize(1)

            shownState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            val confirmingState = consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last()
            confirmingState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Loading }
            runCurrent()

            // It answers from before the cancel, and the read after the cancel fails.
            staleRead.complete(Result.success(aRecoveryStatus(pending = true)))
            val doneState = consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Uninitialized }.last()
            assertThat(reads).isEmpty()
            assertThat(doneState.pendingRecovery).isNull()
            assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId).isEqualTo(R.string.gua_account_recovery_cancelled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a confirmation that was never asked for sends nothing`() = runTest {
        var cancelCalls = 0
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.success(aRecoveryStatus(pending = true)) },
                cancelAccountRecoveryResult = {
                    cancelCalls++
                    Result.success(Unit)
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            shownState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            runCurrent()
            assertThat(cancelCalls).isEqualTo(0)
            // No confirmation dialog and no progress either: the state does not move at all.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the confirmation cancels nothing`() = runTest {
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.success(aRecoveryStatus(pending = true)) },
                // The default would fail the test if a cancel were sent.
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            shownState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            val confirmingState = consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last()
            confirmingState.eventSink(AccountRecoveryBannerEvent.DismissCancelConfirmation)
            val dismissedState = consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Uninitialized }.last()
            assertThat(dismissedState.pendingRecovery).isNotNull()
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed cancel keeps the warning up and says it did not work`() = runTest {
        var statusReads = 0
        val snackbarDispatcher = SnackbarDispatcher()
        val presenter = createAccountRecoveryBannerPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ ->
                    statusReads++
                    Result.success(aRecoveryStatus(pending = true))
                },
                cancelAccountRecoveryResult = { Result.failure(ResolverError.Server(500)) },
            ),
            snackbarDispatcher = snackbarDispatcher,
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            val shownState = consumeItemsUntilPredicate { it.pendingRecovery != null }.last()
            shownState.eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            val confirmingState = consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last()
            confirmingState.eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)
            val failedState = consumeItemsUntilPredicate { it.cancelAction == AsyncAction.Uninitialized }.last()
            assertThat(failedState.pendingRecovery).isNotNull()
            assertThat(statusReads).isEqualTo(1)
            assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId).isEqualTo(R.string.gua_account_recovery_cancel_failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.createAccountRecoveryBannerPresenter(
        identityServiceClient: FakeIdentityServiceClient = FakeIdentityServiceClient(),
        sessionStore: SessionStore = InMemorySessionStore(
            listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = AN_ACCESS_TOKEN)),
        ),
        systemClock: SystemClock = FakeSystemClock(epochMillisResult = (A_COMPLETABLE_AT_EPOCH_SECONDS - 3600) * 1000),
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ) = AccountRecoveryBannerPresenter(
        matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID),
        sessionStore = sessionStore,
        identityServiceClient = identityServiceClient,
        systemClock = systemClock,
        snackbarDispatcher = snackbarDispatcher,
    )

    private companion object {
        const val AN_ACCESS_TOKEN = "an-access-token"
        const val A_REFRESHED_ACCESS_TOKEN = "a-refreshed-access-token"
    }
}
