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
 * GUA FORK: the change-phone-number flow against the real `/account` contract.
 *
 * Three things these tests hold down. First the ordering: nothing may ask the identity service to
 * text the NEW number before a step-up factor has been offered, and `startPhoneChange` is the only
 * call that can. Second the factor branch: it reads the server's factor signal, so an account with a
 * passkey and no PIN is not told to create a PIN, and a status that could not be read is treated as
 * unknown rather than as "no factor". Third the reauthentication: the user says which number is on
 * the account, that number is what both reauth calls carry, and a number that is not the account's
 * is refused with one wording that says nothing about who else might hold it.
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

            // The current number is asked for first, and asking for it costs nothing.
            val currentStep = awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(client.startReauthCalls).isEmpty()
            currentStep.eventSink(ChangePhoneNumberEvents.PhoneChanged(A_CURRENT_LOCAL_DIGITS))
            awaitFirst { it.localPhoneNumber == A_CURRENT_LOCAL_DIGITS }.eventSink(ChangePhoneNumberEvents.Continue)

            val state = awaitPhase(ChangePhoneNumberPhase.EnteringReauthOtp)
            // The OTP went to the number already on file, not to a new one.
            assertThat(client.startReauthCalls).containsExactly(A_CURRENT_PHONE to A_LANGUAGE_TAG)
            assertThat(client.startPhoneChangeCalls).isEmpty()
            assertThat(state.errorMessage).isNull()
            // The field is handed to the new-number step empty, never pre-filled with the number
            // being replaced.
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
            // Back on the same step to try again, with the one neutral reason. Nothing here can say
            // that the number is unknown, or that somebody else holds it.
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(state.errorMessage).isEqualTo(R.string.screen_change_phone_current_mismatch)
            // The refusal is the end of it: no token, and no code to type.
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

            // The real store reads a database, so the token read is still in flight while the user
            // can go on tapping. That wait is the whole window this guards, and an in-memory store
            // answers without ever leaving it, so the test holds the read open itself.
            val tokenRead = CompletableDeferred<Unit>()
            sessionStore.gate = tokenRead

            // Both taps land before the session token has even been read. A second one getting
            // through is a second text to the account's own number and a second of the five hourly
            // wrong-number attempts the server meters per account.
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

            // The sixth digit submits by itself, so the button is still on screen while the token
            // read suspends. Tapping it there used to spend the same code a second time, which the
            // server counts as another wrong-code attempt.
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
            // The server keeps no pending record between the two calls, so verify has to carry the
            // number again, and it must be the same one that was checked to send the code.
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
            submitCurrentNumber()
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

            // The PIN was set up elsewhere, on the two-step verification screen, and this screen
            // sees it only because coming back re-reads.
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)
            val unblocked = awaitPhase(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(unblocked.stepUpBlock).isNull()
            assertThat(client.factorStatusCalls).hasSize(2)
            // Carrying on is not sending: the user still has to say which number is on the account.
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
            // Still blocked, because this build cannot assert a passkey, but blocked for the reason
            // that is now true. The passkey button is gone: pressing it again could only be refused.
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
            // The re-read is for the interstitials only. Landing here would throw the user out of
            // the code step they are on, holding a code that was already texted to them.
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
            // The code was fine; the number was not, so there is nothing to retry on the code step.
            // iOS routes both this and the mismatch back here, and one refusal gets one explanation.
            assertThat(state.phase).isEqualTo(ChangePhoneNumberPhase.EnteringCurrentPhone)
            assertThat(state.errorMessage).isEqualTo(R.string.screen_two_step_verification_phone_invalid)
            assertThat(client.startPhoneChangeCalls).isEmpty()
        }
    }

    /** Drives the flow to the point where the new number has been submitted. */
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

    /** Types the number the account is bound to and submits it, which is what sends the reauth OTP. */
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

    /**
     * The screen re-reads the account's factors on every resume, so the composition needs a
     * lifecycle. It starts un-resumed by default, which is what keeps that read out of the tests
     * that are not about it.
     */
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
        /** While this is set, a read waits on it, so a test can tap again during one. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getSession(sessionId: String): SessionData? {
            gate?.await()
            return delegate.getSession(sessionId)
        }
    }

    private companion object {
        const val MAX_EMISSIONS = 20

        /** Typed as national digits on the US device country the tests run with. */
        const val A_CURRENT_LOCAL_DIGITS = "5559876543"
        const val A_CURRENT_PHONE = "+15559876543"

        /** What the presenter sends as Accept-Language; the tests run under the default locale. */
        val A_LANGUAGE_TAG: String = Locale.getDefault().toLanguageTag()
    }
}
