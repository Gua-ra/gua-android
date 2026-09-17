/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.twostepverification

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
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
import io.element.android.tests.testutils.WarmUpRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: the two-step-verification overview reads the account's FACTORS, not its PIN.
 *
 * A passkey holder with no PIN has two-step verification on, and a status that could not be read is
 * unknown rather than off. Both used to render as "off", which is what put a "Set up PIN" call to
 * action in front of people who already held the stronger factor.
 *
 * They also hold down where a FIRST PIN can come from: the authenticated web ceremony and nowhere
 * else. A bearer session on its own must not be able to add a durable factor, so no path on this
 * screen may reach a native first-PIN call again.
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
            // The PIN row still reads "set up", because there is genuinely no PIN (false, not the
            // null of an unread status), but the screen no longer claims the account has no
            // two-step verification.
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
            // Null, not false: claiming "off" on a failed read is how a passkey holder got nagged.
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
            // Null, not false. False here means "we know this account has no PIN", which is the
            // claim that put "Set up PIN" in front of a PIN holder.
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

            // Nothing moved. Enrolling a PIN on an account that already holds one is refused by the
            // server, and enrolling a passkey it already holds is refused by the authenticator, so
            // guessing either from a status we could not read only buys a dead end.
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
            // No native PIN steps at all: the step-up that has to come first can only run in the
            // browser, so the screen never collects a first PIN itself.
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
            // Our view of the account was stale, not the user's request. The row corrects itself so
            // the next tap is the change flow rather than the same refusal again.
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_pin_already_set)
            assertThat(state.hasPin).isTrue()
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

            // PIN-FIRST: the current PIN is asked for before anything is sent.
            assertThat(awaitFirst { it.phase == TwoStepVerificationPhase.EnteringCurrent }.hasPin).isTrue()
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
        block: suspend ReceiveTurbine<TwoStepVerificationState>.() -> Unit,
    ) {
        moleculeFlow(RecompositionMode.Immediate) { present() }.test { block() }
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
