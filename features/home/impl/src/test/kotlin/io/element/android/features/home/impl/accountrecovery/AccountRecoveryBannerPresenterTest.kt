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
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
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
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class AccountRecoveryBannerPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `nothing is read before the screen resumes, and a live recovery then shows with its time`() = runTest {
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
            assertThat(shownState.pendingRecovery).isEqualTo(
                PendingAccountRecovery(finishableAfter = "${A_COMPLETABLE_AT_EPOCH_SECONDS * 1000} Full false")
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
            assertThat(shownState.pendingRecovery).isEqualTo(PendingAccountRecovery(finishableAfter = null))
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
        systemClock: FakeSystemClock = FakeSystemClock(epochMillisResult = (A_COMPLETABLE_AT_EPOCH_SECONDS - 3600) * 1000),
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ) = AccountRecoveryBannerPresenter(
        matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID),
        sessionStore = sessionStore,
        identityServiceClient = identityServiceClient,
        dateFormatter = FakeDateFormatter(),
        systemClock = systemClock,
        snackbarDispatcher = snackbarDispatcher,
    )

    private companion object {
        const val AN_ACCESS_TOKEN = "an-access-token"
    }
}
