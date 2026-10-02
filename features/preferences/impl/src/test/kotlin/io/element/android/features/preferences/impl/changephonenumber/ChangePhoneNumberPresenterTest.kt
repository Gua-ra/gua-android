/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.lifecycle.Lifecycle
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.libraries.guaresolver.AccountFactorStatus
import io.element.android.libraries.guaresolver.AuthFactor
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.phonenumberentry.FakeDeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.libraries.sessionstorage.api.SessionData
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.FakeLifecycleOwner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.withFakeLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * Three things these tests hold down. The ordering: nothing may ask the identity service to text the
 * new number before a step-up factor has been offered, and `startPhoneChange` is the only call that
 * can. The factor branch: an account with a passkey and no PIN is not told to create a PIN, and a
 * status that could not be read is unknown rather than "no factor". The reauthentication: both reauth
 * calls carry the number the user typed, and a number that is not the account's is refused with one
 * wording that says nothing about who else might hold it.
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

            val currentStep = awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(client.startReauthCalls).isEmpty()
            currentStep.eventSink(ChangePhoneNumberEvents.PhoneChanged(A_CURRENT_LOCAL_DIGITS))
            awaitFirst { it.localPhoneNumber == A_CURRENT_LOCAL_DIGITS }.eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            assertThat(client.startReauthCalls).containsExactly(A_CURRENT_PHONE to A_LANGUAGE_TAG)
            assertThat(client.startPhoneChangeCalls).isEmpty()
            assertThat(state.errorMessage).isNull()
            assertThat(state.localPhoneNumber).isEmpty()
        }
    }

    @Test
    fun `present - a number that is not the account's is refused without saying whose it is`() = runTest {
        val client = FakeIdentityServiceClient(
            startReauthResult = { Result.failure(ResolverError.ReauthPhoneMismatch) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(state.errorMessage).isEqualTo(R.string.screen_change_phone_current_mismatch)
            assertThat(client.verifyReauthCalls).isEmpty()
            assertThat(client.startPhoneChangeCalls).isEmpty()
        }
    }

    @Test
    fun `present - a double tap on the current number sends one reauth OTP, not two`() = runTest {
        val client = FakeIdentityServiceClient()
        val sessionStore = GatedSessionStore(InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))))
        val presenter = createChangePhoneNumberPresenter(client = client, sessionStore = sessionStore)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
                .eventSink(ChangePhoneNumberEvents.PhoneChanged(A_CURRENT_LOCAL_DIGITS))
            val filled = awaitFirst { it.localPhoneNumber == A_CURRENT_LOCAL_DIGITS }

            // An in-memory store answers without suspending, so the test holds the read open itself.
            val tokenRead = CompletableDeferred<Unit>()
            sessionStore.gate = tokenRead

            filled.eventSink(ChangePhoneNumberEvents.Continue)
            filled.eventSink(ChangePhoneNumberEvents.Continue)
            tokenRead.complete(Unit)
            advanceUntilIdle()

            assertThat(client.startReauthCalls).containsExactly(A_CURRENT_PHONE to A_LANGUAGE_TAG)
            assertThat(expectMostRecentItem().phase).isEqualTo(ChangePhoneNumberPhase.EnteringReauthOtp)
        }
    }

    @Test
    fun `present - typing the last digit and tapping Continue verifies the code once, not twice`() = runTest {
        val client = FakeIdentityServiceClient()
        val sessionStore = GatedSessionStore(InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))))
        val presenter = createChangePhoneNumberPresenter(client = client, sessionStore = sessionStore)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()
            val typed = awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)

            val tokenRead = CompletableDeferred<Unit>()
            sessionStore.gate = tokenRead
            typed.eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))
            typed.eventSink(ChangePhoneNumberEvents.Continue)
            tokenRead.complete(Unit)
            advanceUntilIdle()

            assertThat(client.verifyReauthCalls).hasSize(1)
            assertThat(expectMostRecentItem().phase).isEqualTo(ChangePhoneNumberPhase.EnteringPin)
        }
    }

    @Test
    fun `present - the attempt cap on the account's own number is surfaced as a rate limit`() = runTest {
        val client = FakeIdentityServiceClient(
            startReauthResult = { Result.failure(ResolverError.RateLimited) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_rate_limited)
        }
    }

    @Test
    fun `present - the same current number is submitted to start and to verify`() = runTest {
        val client = FakeIdentityServiceClient()
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()
            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))

            awaitPhase(ChangePhoneNumberPhase.EnteringPin)
            assertThat(client.verifyReauthCalls).containsExactly(A_CURRENT_PHONE to "111111")
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
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.PasskeyNotUsableHere)
            assertThat(state.canSetUpPasskey).isFalse()
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
            submitCurrentNumber()
            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))

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
            startPhoneChangeResult = { Result.failure(ResolverError.StepUpRequired) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            runToNewPhoneStep(client)

            val state = awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.NoFactorRegistered)
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
            factorStatusResult = {
                Result.success(
                    aFactorStatus(hasPin = true, phoneChangeStepUpFactors = listOf(AuthFactor.PASSKEY, AuthFactor.PIN))
                )
            },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()

            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            assertThat(client.startReauthCalls).hasSize(1)
        }
    }

    @Test
    fun `a PIN registered while the block was up lets the flow carry on when the screen comes back`() = runTest {
        val results = ArrayDeque(
            listOf(
                aFactorStatus(hasPin = false, passkeyRegistered = false),
                aFactorStatus(hasPin = true, passkeyRegistered = false),
            )
        )
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(results.removeFirstOrNull() ?: aFactorStatus(hasPin = true)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        val lifecycleOwner = FakeLifecycleOwner()
        presenter.test(lifecycleOwner) {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            val blocked = awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(blocked.stepUpBlock).isEqualTo(StepUpBlock.NoFactorRegistered)
            assertThat(client.factorStatusCalls).hasSize(1)

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            val unblocked = awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(unblocked.stepUpBlock).isNull()
            assertThat(client.factorStatusCalls).hasSize(2)
            assertThat(client.startReauthCalls).isEmpty()
        }
    }

    @Test
    fun `a passkey registered while the block was up withdraws the button that would be refused`() = runTest {
        val results = ArrayDeque(
            listOf(
                aFactorStatus(hasPin = false, passkeyRegistered = false),
                aFactorStatus(hasPin = false, passkeyRegistered = true),
            )
        )
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(results.removeFirstOrNull() ?: aFactorStatus(passkeyRegistered = true)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        val lifecycleOwner = FakeLifecycleOwner()
        presenter.test(lifecycleOwner) {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            assertThat(awaitPhase(ChangePhoneNumberPhase.NeedsStepUp).canSetUpPasskey).isTrue()

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            val restated = awaitFirst { it.stepUpBlock == StepUpBlock.PasskeyNotUsableHere }
            assertThat(restated.phase).isEqualTo(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(restated.canSetUpPasskey).isFalse()
            assertThat(restated.canSetUpPin).isTrue()
        }
    }

    @Test
    fun `a re-read that fails leaves the block exactly as it was`() = runTest {
        val results = ArrayDeque<Result<AccountFactorStatus>>(
            listOf(
                Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)),
                Result.failure(ResolverError.Server(500)),
            )
        )
        val client = FakeIdentityServiceClient(
            factorStatusResult = { results.removeFirstOrNull() ?: Result.failure(ResolverError.Server(500)) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        val lifecycleOwner = FakeLifecycleOwner()
        presenter.test(lifecycleOwner) {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.NeedsStepUp)

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            advanceUntilIdle()
            assertThat(client.factorStatusCalls).hasSize(2)
            val state = expectMostRecentItem()
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(state.stepUpBlock).isEqualTo(StepUpBlock.NoFactorRegistered)
        }
    }

    @Test
    fun `a resume in the middle of the flow reads nothing and moves nobody`() = runTest {
        val client = FakeIdentityServiceClient()
        val presenter = createChangePhoneNumberPresenter(client = client)
        val lifecycleOwner = FakeLifecycleOwner()
        presenter.test(lifecycleOwner) {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()
            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            assertThat(client.factorStatusCalls).hasSize(1)

            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            advanceUntilIdle()
            assertThat(client.factorStatusCalls).hasSize(1)
            assertThat(expectMostRecentItem().phase).isEqualTo(ChangePhoneNumberPhase.EnteringReauthOtp)
        }
    }

    @Test
    fun `a passkey the account already holds is named, not reported as a server failure`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
            passkeyEnrollmentResult = { Result.failure(ResolverError.PasskeyAlreadyRegistered) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.NeedsStepUp).eventSink(ChangePhoneNumberEvents.SetUpPasskey)

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_passkey_already_registered)
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.NeedsStepUp)
            assertThat(state.passkeyEnrollUrl).isNull()
        }
    }

    @Test
    fun `an account that can settle no step-up here is pointed at recovery`() = runTest {
        val client = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
            passkeyEnrollmentResult = { Result.failure(ResolverError.StepUpUnavailable) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            awaitPhase(ChangePhoneNumberPhase.NeedsStepUp).eventSink(ChangePhoneNumberEvents.SetUpPasskey)

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_step_up_unavailable)
        }
    }

    @Test
    fun `a number that stops parsing at verify goes back to the number step, not the code step`() = runTest {
        val client = FakeIdentityServiceClient(
            verifyReauthResult = { Result.failure(ResolverError.InvalidPhoneNumber) },
        )
        val presenter = createChangePhoneNumberPresenter(client = client)
        presenter.test {
            awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
            submitCurrentNumber()
            awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))

            val state = awaitFirst { it.errorMessage != null }
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_phone_invalid)
            assertThat(client.startPhoneChangeCalls).isEmpty()
        }
    }

    private suspend fun ReceiveTurbine<ChangePhoneNumberState>.runToNewPhoneStep(
        client: FakeIdentityServiceClient,
    ) {
        awaitItem().eventSink(ChangePhoneNumberEvents.Continue)
        submitCurrentNumber()
        awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp).eventSink(ChangePhoneNumberEvents.CodeChanged("111111"))
        awaitPhase(ChangePhoneNumberPhase.EnteringPin).eventSink(ChangePhoneNumberEvents.CodeChanged("246813"))
        awaitPhase(ChangePhoneNumberPhase.EnteringNewPhone).eventSink(ChangePhoneNumberEvents.PhoneChanged("5551234567"))
        awaitFirst { it.localPhoneNumber.isNotEmpty() }.eventSink(ChangePhoneNumberEvents.Continue)
        assertThat(client.startReauthCalls).hasSize(1)
    }

    private suspend fun ReceiveTurbine<ChangePhoneNumberState>.submitCurrentNumber() {
        awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
            .eventSink(ChangePhoneNumberEvents.PhoneChanged(A_CURRENT_LOCAL_DIGITS))
        awaitFirst { it.localPhoneNumber == A_CURRENT_LOCAL_DIGITS }.eventSink(ChangePhoneNumberEvents.Continue)
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

    /** Starts un-resumed, which keeps the resume read out of tests that are not about it. */
    private suspend fun ChangePhoneNumberPresenter.test(
        lifecycleOwner: FakeLifecycleOwner = FakeLifecycleOwner(),
        block: suspend ReceiveTurbine<ChangePhoneNumberState>.() -> Unit,
    ) {
        moleculeFlow(RecompositionMode.Immediate) {
            withFakeLifecycleOwner(lifecycleOwner) { present() }
        }.test { block() }
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

    /** A [SessionStore] whose read can be held in flight, the way the database-backed one lags. */
    private class GatedSessionStore(private val delegate: SessionStore) : SessionStore by delegate {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getSession(sessionId: String): SessionData? {
            gate?.await()
            return delegate.getSession(sessionId)
        }
    }

    private companion object {
        const val MAX_EMISSIONS = 20

        const val A_CURRENT_LOCAL_DIGITS = "5559876543"
        const val A_CURRENT_PHONE = "+15559876543"

        val A_LANGUAGE_TAG: String = Locale.getDefault().toLanguageTag()
    }
}
