/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.libraries.guaresolver.AuthFactor
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
 * GUA FORK: the change-phone-number flow against the real `/account` contract.
 *
 * Two things these tests hold down. First the ordering: nothing may ask the identity service to text
 * the NEW number before a step-up factor has been offered, and `startPhoneChange` is the only call
 * that can. Second the factor branch: it reads the server's factor signal, so an account with a
 * passkey and no PIN is not told to create a PIN, and a status that could not be read is treated as
 * unknown rather than as "no factor".
 */
class ChangePhoneNumberPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - the intro sends nothing at all`() = runTest {
        val client = FakeIdentityServiceClient()
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.phase).isEqualTo(ChangePhoneNumberPhase.Intro)
            assertThat(client.startReauthCalls).isEmpty()
            assertThat(client.startPhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - an account with a PIN is reauthenticated before the step-up is asked for`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = true)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            // The OTP went to the number already on file, not to a new one.
            assertThat(client.startReauthCalls).hasSize(1)
            assertThat(client.startPhoneChangeCalls).isEmpty()
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `present - an account with a passkey and no PIN is never told to create a PIN`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)
            // It holds a factor, so this is NOT the "you have no two-step verification" block, and
            // enrolling another passkey is not offered because the account already has one.
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.PasskeyNotUsableHere)
            assertThat(state.canSetUpPasskey).isFalse()
            // Hard: nothing was sent, and the flow did not continue behind the block.
            assertThat(client.startReauthCalls).isEmpty()
            assertThat(client.startPhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - an account with no factor at all is offered a passkey as well as a PIN`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.NoFactorRegistered)
            // A choice between the two factors, not the old hardcoded PIN funnel.
            assertThat(state.canSetUpPasskey).isTrue()
            assertThat(state.canSetUpPin).isTrue()
            assertThat(client.startReauthCalls).isEmpty()
        }
    }

    @Test
    fun `present - choosing the passkey on the block starts enrollment instead of the PIN flow`() = runTest {
        var pinSetupOpened = false
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false)) },
        )
        val presenter = createChangePhoneNumberPresenter(
            client = client,
            navigateToPinSetup = { pinSetupOpened = true },
        )
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.NeedsStepUp).eventSink(ChangePhoneNumberEvents.SetUpPasskey)

            val state = awaitFirst { it.passkeyEnrollUrl != null }
            assertThat(state.passkeyEnrollUrl).isEqualTo(FakeIdentityServiceClient.AN_ENROLL_URL)
            assertThat(client.passkeyEnrollmentCalls).hasSize(1)
            assertThat(pinSetupOpened).isFalse()
            // Still blocked: registering a factor is the way out, not a way through.
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.NeedsStepUp)
        }
    }

    @Test
    fun `present - a factor status that cannot be read is unknown, not no-factor`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.failure(ResolverError.Transport(RuntimeException("offline"))) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitFirst { it.errorMessage != null }
            // Back on the intro with an error. Guessing "no factor" here is what sent passkey
            // holders into PIN setup.
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.Intro)
            assertThat(state.stepUpBlock).isNull()
            assertThat(client.startReauthCalls).isEmpty()
        }
    }

    @Test
    fun `present - an active fresh-2FA hold stops the flow before any OTP`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(changePhoneCooldownRemainingSeconds = 3600)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.Cooldown)
            assertThat(state.cooldownRemainingSeconds).isEqualTo(3600)
            assertThat(client.startReauthCalls).isEmpty()
        }
    }

    @Test
    fun `present - the new number is only texted once the reauth token and the PIN are both in hand`() = runTest {
        val client = FakeIdentityServiceClient()
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))

            // The step-up is collected next, and it still costs the new number nothing.
            val pinState = awaitPhase(ChangePhoneNumberPhase.EnteringPin)
            assertThat(client.startPhoneChangeCalls).isEmpty()
            pinState.eventSink(ChangePhoneNumberEvents.CodeChanged("246813"))

            val phoneState = awaitPhase(ChangePhoneNumberPhase.EnteringNewPhone)
            assertThat(client.startPhoneChangeCalls).isEmpty()
            phoneState.eventSink(ChangePhoneNumberEvents.PhoneChanged("5551234567"))
            awaitFirst { it.localPhoneNumber.isNotEmpty() }.eventSink(ChangePhoneNumberEvents.Continue)

            awaitPhase(ChangePhoneNumberPhase.EnteringOtp)
            assertThat(client.startPhoneChangeCalls).hasSize(1)
            val call = client.startPhoneChangeCalls.single()
            assertThat(call.reauthToken).isEqualTo(FakeIdentityServiceClient.A_REAUTH_TOKEN)
            assertThat(call.pin).isEqualTo("246813")
            assertThat(call.newPhone).isEqualTo("+15551234567")
        }
    }

    @Test
    fun `present - a step-up refusal at start terminates instead of retrying on the reauth token`() = runTest {
        val client = FakeIdentityServiceClient(
            // The pre-check saw a PIN; the server disagreed by the time the change was submitted.
            startPhoneChangeResult = { Result.failure(ResolverError.StepUpRequired) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)

            val state = awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.NoFactorRegistered)
            // One attempt, then the operation ends. No second start, and no new reauth OTP.
            assertThat(client.startPhoneChangeCalls).hasSize(1)
            assertThat(client.startReauthCalls).hasSize(1)
            assertThat(client.completePhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - a cooldown discovered mid-flow still holds the change`() = runTest {
        val client = FakeIdentityServiceClient(
            startPhoneChangeResult = { Result.failure(ResolverError.TwoFactorCooldown(retryAfterSeconds = 7200)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)

            val state = awaitPhase(ChangePhoneNumberPhase.Cooldown)
            assertThat(state.cooldownRemainingSeconds).isEqualTo(7200)
            assertThat(client.completePhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - a wrong PIN spends the reauth token, so the flow restarts rather than retrying`() = runTest {
        val client = FakeIdentityServiceClient(
            startPhoneChangeResult = { Result.failure(ResolverError.InvalidPin) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)

            val state = awaitFirst { it.phase == ChangePhoneNumberPhase.Intro && it.errorMessage != null }
            // Back to the start: the token the server burned cannot be offered again, and nothing
            // re-sends an OTP on its own.
            assertThat(client.startReauthCalls).hasSize(1)
            assertThat(client.startPhoneChangeCalls).hasSize(1)
            assertThat(state.stepUpBlock).isNull()
        }
    }

    @Test
    fun `present - an expired reauth token restarts instead of falling through`() = runTest {
        val client = FakeIdentityServiceClient(
            startPhoneChangeResult = { Result.failure(ResolverError.InvalidReauthToken) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)

            val state = awaitFirst { it.phase == ChangePhoneNumberPhase.Intro && it.errorMessage != null }
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.Intro)
            assertThat(client.completePhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - the new-number OTP completes the change against the challenge`() = runTest {
        val client = FakeIdentityServiceClient()
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)
            awaitPhase(ChangePhoneNumberPhase.EnteringOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("999999"))

            awaitPhase(ChangePhoneNumberPhase.Done)
            assertThat(client.completePhoneChangeCalls).containsExactly(
                FakeIdentityServiceClient.A_CHALLENGE_ID to "999999"
            )
        }
    }

    @Test
    fun `present - an old identity-service that lists no step-up factors still allows a PIN holder through`() = runTest {
        val client = FakeIdentityServiceClient(
            // The list is what DefaultIdentityServiceClient fills in when the server omits the field.
            factorStatusResult = {
                Result.success(
                    aFactorStatus(hasPin = true, phoneChangeStepUpFactors = listOf(AuthFactor.PASSKEY, AuthFactor.PIN))
                )
            },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)

            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            assertThat(client.startReauthCalls).hasSize(1)
        }
    }

    /** Drives the flow to the point where the new number has been submitted. */
    private suspend fun ReceiveTurbine<ChangePhoneNumberState>.runToNewPhoneStep(
        client: FakeIdentityServiceClient,
    ) {
        awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
        awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))
        awaitPhase(ChangePhoneNumberPhase.EnteringPin).eventSink(ChangePhoneNumberEvents.CodeChanged("246813"))
        awaitPhase(ChangePhoneNumberPhase.EnteringNewPhone).eventSink(ChangePhoneNumberEvents.PhoneChanged("5551234567"))
        awaitFirst { it.localPhoneNumber.isNotEmpty() }.eventSink(ChangePhoneNumberEvents.Continue)
        assertThat(client.startReauthCalls).hasSize(1)
    }

    private suspend fun ReceiveTurbine<ChangePhoneNumberState>.awaitPhase(
        phase: ChangePhoneNumberPhase,
    ): ChangePhoneNumberState = awaitFirst { it.phase == phase }

    private suspend fun ReceiveTurbine<ChangePhoneNumberState>.awaitFirst(
        predicate: (ChangePhoneNumberState) -> Boolean,
    ): ChangePhoneNumberState {
        repeat(MAX_EMISSIONS) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
        error("No matching state after $MAX_EMISSIONS emissions")
    }

    private suspend fun ChangePhoneNumberPresenter.test(block: suspend ReceiveTurbine<ChangePhoneNumberState>.() -> Unit) {
        moleculeFlow(RecompositionMode.Immediate) { present() }.test { block() }
    }

    private fun createChangePhoneNumberPresenter(
        client: IdentityServiceClient = FakeIdentityServiceClient(),
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
        navigateToCountryPicker: () -> Unit = {},
        navigateToPinSetup: () -> Unit = {},
    ) = ChangePhoneNumberPresenter(
        navigateToCountryPicker = navigateToCountryPicker,
        navigateToPinSetup = navigateToPinSetup,
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
