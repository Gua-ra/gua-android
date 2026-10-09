/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.accountrecovery

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.FakeLifecycleOwner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.testWithLifecycleOwner
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AccountRecoveryBannerTokenRefreshTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `a refused token is refreshed and the read retried at once`() = runTest {
        val statusReads = mutableListOf<String>()
        val presenter = createAccountRecoveryBannerPresenter(
            FakeIdentityServiceClient(
                accountFactorStatusResult = { token, _ ->
                    statusReads += token
                    if (token == AN_ACCESS_TOKEN) Result.failure(ResolverError.Server(401)) else Result.success(aRecoveryStatus(pending = true))
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            runCurrent()
            assertThat(statusReads).containsExactly(AN_ACCESS_TOKEN, A_REFRESHED_ACCESS_TOKEN).inOrder()
            assertThat(expectMostRecentItem().pendingRecovery).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cancel refused for its token is refreshed and sent again`() = runTest {
        var cancelled = false
        val cancelCalls = mutableListOf<String>()
        val presenter = createAccountRecoveryBannerPresenter(
            FakeIdentityServiceClient(
                accountFactorStatusResult = { _, _ -> Result.success(aRecoveryStatus(pending = !cancelled)) },
                cancelAccountRecoveryResult = { token ->
                    cancelCalls += token
                    if (token == AN_ACCESS_TOKEN) {
                        Result.failure(ResolverError.Server(401))
                    } else {
                        cancelled = true
                        Result.success(Unit)
                    }
                },
            ),
        )
        presenter.testWithLifecycleOwner(FakeLifecycleOwner(Lifecycle.State.RESUMED)) {
            consumeItemsUntilPredicate { it.pendingRecovery != null }.last().eventSink(AccountRecoveryBannerEvent.CancelRecovery)
            consumeItemsUntilPredicate { it.cancelAction.isConfirming() }.last().eventSink(AccountRecoveryBannerEvent.ConfirmCancelRecovery)

            val doneState = consumeItemsUntilPredicate {
                it.pendingRecovery == null && it.cancelAction == AsyncAction.Uninitialized
            }.last()
            assertThat(doneState.pendingRecovery).isNull()
            assertThat(cancelCalls).containsExactly(AN_ACCESS_TOKEN, A_REFRESHED_ACCESS_TOKEN).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createAccountRecoveryBannerPresenter(
        identityServiceClient: FakeIdentityServiceClient,
    ): AccountRecoveryBannerPresenter {
        // An SDK that holds the first token until a refresh replaces it.
        var token = AN_ACCESS_TOKEN
        return AccountRecoveryBannerPresenter(
            matrixClient = FakeMatrixClient(
                sessionId = A_SESSION_ID,
                accessTokenLambda = { token },
                refreshAccessTokenLambda = {
                    token = A_REFRESHED_ACCESS_TOKEN
                    token
                },
            ),
            sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = AN_ACCESS_TOKEN))),
            identityServiceClient = identityServiceClient,
            systemClock = FakeSystemClock(epochMillisResult = (A_COMPLETABLE_AT_EPOCH_SECONDS - 3600) * 1000),
            snackbarDispatcher = SnackbarDispatcher(),
        )
    }

    private companion object {
        const val AN_ACCESS_TOKEN = "an-access-token"
        const val A_REFRESHED_ACCESS_TOKEN = "a-refreshed-access-token"
    }
}
