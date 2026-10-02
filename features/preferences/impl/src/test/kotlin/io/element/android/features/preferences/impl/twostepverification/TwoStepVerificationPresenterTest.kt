/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import androidx.lifecycle.Lifecycle
import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.FakeDeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.FakeLifecycleOwner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.testWithLifecycleOwner
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The overview reads the account's factors, not its PIN: a passkey holder with no PIN has two-step
 * verification on, and a status that could not be read is unknown rather than off.
 *
 * A first PIN can only come from the authenticated web ceremony, so no path on this screen may reach
 * a native first-PIN call.
 */
class TwoStepVerificationPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - a passkey with no PIN reads as two-step verification on`() = runTest {
        val presenter = createTwoStepVerificationPresenter(
            client = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            ),
        )
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.twoStepVerificationOn).isTrue()
            assertThat(state.passkeyRegistered).isTrue()
            assertThat(state.hasPin).isFalse()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `present - a PIN with no passkey reads as on`() = runTest {
        val presenter = createTwoStepVerificationPresenter(
            client = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = true, passkeyRegistered = false)) },
            ),
        )
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.twoStepVerificationOn).isTrue()
            assertThat(state.hasPin).isTrue()
            assertThat(state.passkeyRegistered).isFalse()
        }
    }

    @Test
    fun `present - an account with neither factor reads as off`() = runTest {
        val presenter = createTwoStepVerificationPresenter(
            client = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
            ),
        )
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.twoStepVerificationOn).isFalse()
        }
    }

    @Test
    fun `present - a status that cannot be read is unknown, not off`() = runTest {
        val presenter = createTwoStepVerificationPresenter(
            client = FakeIdentityServiceClient(
                factorStatusResult = { Result.failure(ResolverError.Transport(RuntimeException("offline"))) },
            ),
        )
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.twoStepVerificationOn).isNull()
            assertThat(state.errorMessage).isNotNull()
        }
    }

    @Test
    fun `present - no session token means unknown too`() = runTest {
        val presenter = createTwoStepVerificationPresenter(sessionStore = InMemorySessionStore())
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.twoStepVerificationOn).isNull()
            assertThat(state.hasPin).isNull()
            assertThat(state.passkeyRegistered).isNull()
        }
    }

    @Test
    fun `present - an unreadable status starts neither PIN flow`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.failure(ResolverError.Transport(RuntimeException("offline"))) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            val state = awaitFirst { it.phase == TwoStepVerificationPhase.Overview }
            assertThat(state.hasPin).isNull()

            state.eventSink(TwoStepVerificationEvent.StartSetup)
            state.eventSink(TwoStepVerificationEvent.StartChange)
            state.eventSink(TwoStepVerificationEvent.SetUpPasskey)

            expectNoEvents()
            assertThat(client.pinEnrollmentCalls).isEmpty()
            assertThat(client.passkeyEnrollmentCalls).isEmpty()
        }
    }

    @Test
    fun `present - a known account with no PIN enrolls its first PIN in the web ceremony`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.StartSetup)

            val state = awaitFirst { it.factorEnrollUrl != null }
            assertThat(state.factorEnrollUrl).isEqualTo(FakeIdentityServiceClient.A_PIN_ENROLL_URL)
            assertThat(client.pinEnrollmentCalls).hasSize(1)
            assertThat(state.phase).isEqualTo(TwoStepVerificationPhase.Overview)
        }
    }

    @Test
    fun `present - an account that turns out to already have a PIN is offered the change instead`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
            pinEnrollmentResult = { Result.failure(ResolverError.PinAlreadySet) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.StartSetup)

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_pin_already_set)
            assertThat(state.hasPin).isTrue()
            assertThat(state.factorEnrollUrl).isNull()
        }
    }

    @Test
    fun `present - an account that turns out to already have a passkey has its row corrected`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = true, passkeyRegistered = false)) },
            passkeyEnrollmentResult = { Result.failure(ResolverError.PasskeyAlreadyRegistered) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.SetUpPasskey)

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_passkey_already_registered)
            assertThat(state.passkeyRegistered).isTrue()
            assertThat(state.factorEnrollUrl).isNull()
        }
    }

    @Test
    fun `present - an account that can produce no proof here is pointed at recovery`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            pinEnrollmentResult = { Result.failure(ResolverError.StepUpUnavailable) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.StartSetup)

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_step_up_unavailable)
            assertThat(state.factorEnrollUrl).isNull()
        }
    }

    @Test
    fun `present - enrolling a passkey opens the same kind of ceremony`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = true, passkeyRegistered = false)) },
        )
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.SetUpPasskey)

            val state = awaitFirst { it.factorEnrollUrl != null }
            assertThat(state.factorEnrollUrl).isEqualTo(FakeIdentityServiceClient.AN_ENROLL_URL)
            assertThat(client.pinEnrollmentCalls).isEmpty()
        }
    }

    @Test
    fun `present - a known account with a PIN still starts the change flow`() = runTest {
        val presenter = createTwoStepVerificationPresenter(
            client = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = true)) },
            ),
        )
        presenter.test {
            awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.eventSink(TwoStepVerificationEvent.StartChange)

            assertThat(awaitFirst { it.phase == TwoStepVerificationPhase.EnteringCurrent }.hasPin).isTrue()
        }
    }

    @Test
    fun `present - the factor status is read again when the screen comes back`() = runTest {
        var hasPin = false
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = hasPin, passkeyRegistered = false)) },
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test(lifecycleOwner) {
            assertThat(awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.hasPin).isFalse()

            hasPin = true
            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)

            val state = awaitFirst { it.hasPin == true }
            assertThat(state.twoStepVerificationOn).isTrue()
            assertThat(client.factorStatusCalls).hasSize(2)
        }
    }

    @Test
    fun `present - a refresh that fails keeps the status already on screen`() = runTest {
        var readable = true
        val client = FakeIdentityServiceClient(
            factorStatusResult = {
                if (readable) {
                    Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true))
                } else {
                    Result.failure(ResolverError.Transport(RuntimeException("offline")))
                }
            },
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        val presenter = createTwoStepVerificationPresenter(client = client)
        presenter.test(lifecycleOwner) {
            assertThat(awaitFirst { it.phase == TwoStepVerificationPhase.Overview }.passkeyRegistered).isTrue()

            readable = false
            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            advanceUntilIdle()

            val state = expectMostRecentItem()
            assertThat(client.factorStatusCalls).hasSize(2)
            assertThat(state.passkeyRegistered).isTrue()
            assertThat(state.twoStepVerificationOn).isTrue()
            assertThat(state.phase).isEqualTo(TwoStepVerificationPhase.Overview)
        }
    }

    private suspend fun ReceiveTurbine<TwoStepVerificationState>.awaitFirst(
        predicate: (TwoStepVerificationState) -> Boolean,
    ): TwoStepVerificationState {
        repeat(MAX_EMISSIONS) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
        error("No matching state after $MAX_EMISSIONS emissions")
    }

    private suspend fun TwoStepVerificationPresenter.test(
        lifecycleOwner: FakeLifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED),
        block: suspend ReceiveTurbine<TwoStepVerificationState>.() -> Unit,
    ) {
        testWithLifecycleOwner(lifecycleOwner) { block() }
    }

    private fun createTwoStepVerificationPresenter(
        client: IdentityServiceClient = FakeIdentityServiceClient(),
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
    ) = TwoStepVerificationPresenter(
        navigateToCountryPicker = {},
        matrixClient = FakeMatrixClient(sessionId = A_USER_ID),
        sessionStore = sessionStore,
        identityServiceClient = client,
        selectedCountryStore = SelectedCountryStore(),
        deviceCountryProvider = FakeDeviceCountryProvider(Country(isoCode = "US", dialCode = "1")),
    )

    private companion object {
        const val MAX_EMISSIONS = 20
    }
}
