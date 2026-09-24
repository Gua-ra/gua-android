/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.lifecycle.Lifecycle
import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.fixtures.A_DEVICE_KEY
import io.element.android.features.preferences.impl.fixtures.FakeAccountAuthorityManager
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aBootstrapChain
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.features.preferences.impl.fixtures.aGenesisRootedChain
import io.element.android.features.preferences.impl.fixtures.aPendingAdoption
import io.element.android.features.preferences.impl.fixtures.aRootedChain
import io.element.android.features.preferences.impl.fixtures.anAuthorityDevice
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityPurpose
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.FakeLifecycleOwner
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.testWithLifecycleOwner
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: where the step-up of ADM-009 decision 4 is produced, and the second recovery route beside it.
 *
 * A class of its own rather than more of [AccountAuthorityPresenterTest], because these tests are about one
 * thing: which factor this account can produce and where. The first of them is the line the whole design rests
 * on, that a passkey-only account reaches adoption without being told to add a PIN.
 */
class AccountAuthorityStepUpPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    /**
     * C4, and the line the whole step-up rests on: a passkey-only account roots itself without a PIN.
     *
     * The account holds a passkey and no PIN. It is not blocked and it is not asked for a PIN: the confirmation
     * runs in the sheet, the app is handed a one-time URL to open, and the record is submitted when this screen
     * comes back with a step-up that carries nothing, because the proof is a row the server wrote.
     */
    @Test
    fun `present - a passkey-only account adopts through the web sheet, and is never asked for a PIN`() =
        runTest {
            val manager = FakeAccountAuthorityManager()
            val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
            val presenter = createPresenter(
                manager = manager,
                identityServiceClient = FakeIdentityServiceClient(
                    factorStatusResult = {
                        Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true))
                    },
                ),
            )

            presenter.test(lifecycleOwner) {
                adoptUpToStepUp(manager)
                val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                assertThat(stepUp.stepUpBlock).isNull()
                assertThat(stepUp.stepUpMethod).isEqualTo(AccountAuthorityStepUpMethod.WebSheet)
                // No PIN arm at all for this account: the field cannot be submitted and the browser button can.
                assertThat(stepUp.canSubmit).isFalse()
                assertThat(stepUp.canConfirmInBrowser).isTrue()

                stepUp.eventSink(AccountAuthorityEvent.ConfirmInBrowser)
                val opened = awaitFirst { it.webStepUpUrl != null }
                assertThat(opened.awaitingWebStepUp).isTrue()
                // Scoped to this transition, and to this one only.
                assertThat(manager.webStepUpCalls).containsExactly(AuthorityPurpose.ADOPT)
                assertThat(manager.adoptCalls).isEmpty()
                opened.eventSink(AccountAuthorityEvent.ClearWebStepUpUrl)

                // The Custom Tab is another activity, so this is the app being left for it and coming back.
                lifecycleOwner.givenState(Lifecycle.State.STARTED)
                lifecycleOwner.givenState(Lifecycle.State.RESUMED)

                val done = awaitFirst { it.successMessage != null }
                assertThat(done.successMessage).isEqualTo(R.string.screen_account_authority_submitted)
                val call = manager.adoptCalls.single()
                // Nothing travels with it: the server spends the proof it recorded for this purpose.
                assertThat(call.stepUp).isEqualTo(AuthorityStepUp.WebSheet)
                assertThat(call.artifactConfirmed).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `present - the sheet is scoped to the transition on screen`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
        )
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            ),
        )

        presenter.test {
            awaitFirst { it.devices.isNotEmpty() }
                .eventSink(AccountAuthorityEvent.StartRevocation(A_DEVICE_KEY))
            awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                .eventSink(AccountAuthorityEvent.ConfirmInBrowser)

            awaitFirst { it.webStepUpUrl != null }
            // A proof taken for one purpose is not a proof for another, so the purpose is the one on screen and
            // never a default.
            assertThat(manager.webStepUpCalls).containsExactly(AuthorityPurpose.REVOKE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - an account with only a PIN is asked here, and no browser is opened`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = true, passkeyRegistered = false)) },
            ),
        )

        presenter.test {
            adoptUpToStepUp(manager)
            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            assertThat(stepUp.stepUpMethod).isEqualTo(AccountAuthorityStepUpMethod.Pin)
            assertThat(stepUp.canConfirmInBrowser).isFalse()
            stepUp.eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            awaitFirst { it.successMessage != null }
            assertThat(manager.adoptCalls.single().stepUp).isEqualTo(AuthorityStepUp.Pin("123456"))
            assertThat(manager.webStepUpCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A factor read that failed says UNKNOWN, and the sheet is the answer that is right either way.
     *
     * Guessing "no factor" is how a passkey holder gets told to create a PIN; guessing "PIN" is how they get a
     * field they have nothing to type into. The page offers whichever factor the account actually holds.
     */
    @Test
    fun `present - a factor status that could not be read takes the sheet, not a PIN field`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.failure(RuntimeException("offline")) },
            ),
        )

        presenter.test {
            adoptUpToStepUp(manager)
            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            assertThat(stepUp.stepUpBlock).isNull()
            assertThat(stepUp.stepUpMethod).isEqualTo(AccountAuthorityStepUpMethod.WebSheet)
            assertThat(stepUp.canSubmit).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a PIN submitted while the step-up runs in the sheet sends nothing`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            ),
        )

        presenter.test {
            adoptUpToStepUp(manager)
            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            // Refused in the presenter, not merely disabled on screen: a submission built from an empty field
            // would spend a challenge to be told no factor was produced.
            stepUp.eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.pin == "123456" }.eventSink(AccountAuthorityEvent.Submit)
            expectNoEvents()
            assertThat(manager.adoptCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a sheet closed without finishing leaves the step-up, and says so`() = runTest {
        val manager = FakeAccountAuthorityManager(
            adoptResult = { Result.failure(AuthorityError.StepUpRequired) },
        )
        val lifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            ),
        )

        presenter.test(lifecycleOwner) {
            adoptUpToStepUp(manager)
            awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                .eventSink(AccountAuthorityEvent.ConfirmInBrowser)
            awaitFirst { it.awaitingWebStepUp }
            lifecycleOwner.givenState(Lifecycle.State.STARTED)
            lifecycleOwner.givenState(Lifecycle.State.RESUMED)

            val refused = awaitFirst { it.errorMessage != null }
            assertThat(refused.errorMessage)
                .isEqualTo(R.string.screen_account_authority_error_step_up_required)
            assertThat(refused.phase).isEqualTo(AccountAuthorityPhase.StepUp)
            // The proof is spent either way, so the way out is a new sheet rather than a second submission.
            assertThat(refused.awaitingWebStepUp).isFalse()
            assertThat(refused.canConfirmInBrowser).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The one step-up that has no sheet, because the server has no purpose to record a proof against.
     *
     * An objection asks for a factor at any age and is not a transition, so there is no sheet for it. A
     * passkey-only account is therefore told that this one step cannot be confirmed here, and pointed at the
     * objection an active device makes for free, rather than at a PIN to go and create.
     */
    @Test
    fun `present - a second objection on a passkey-only account is named, and never sent to a sheet`() =
        runTest {
            val manager = FakeAccountAuthorityManager(
                stateResult = { Result.success(aBootstrapChain(pending = aPendingAdoption())) },
                opposeResult = { Result.failure(AuthorityError.StepUpRequired) },
            )
            val presenter = createPresenter(
                manager = manager,
                identityServiceClient = FakeIdentityServiceClient(
                    factorStatusResult = {
                        Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true))
                    },
                ),
            )

            presenter.test {
                awaitFirst { it.pendingTransition != null }.eventSink(AccountAuthorityEvent.Oppose)

                val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                assertThat(stepUp.stepUp).isEqualTo(AccountAuthorityStepUp.Oppose)
                assertThat(stepUp.stepUpBlock)
                    .isEqualTo(AccountAuthorityStepUpBlock.PasskeyNotUsableForObjection)
                assertThat(stepUp.stepUpMethod).isNull()
                assertThat(stepUp.canConfirmInBrowser).isFalse()
                assertThat(stepUp.canSubmit).isFalse()
                // No sheet is asked for, because there is no purpose one could be scoped to.
                stepUp.eventSink(AccountAuthorityEvent.ConfirmInBrowser)
                expectNoEvents()
                assertThat(manager.webStepUpCalls).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `present - a second objection from an account that holds a PIN asks for it here`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aBootstrapChain(pending = aPendingAdoption())) },
            opposeResult = { Result.failure(AuthorityError.StepUpRequired) },
        )
        val presenter = createPresenter(
            manager = manager,
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = true, passkeyRegistered = true)) },
            ),
        )

        presenter.test {
            awaitFirst { it.pendingTransition != null }.eventSink(AccountAuthorityEvent.Oppose)

            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            assertThat(stepUp.stepUpBlock).isNull()
            // A passkey account, and still the PIN here: the sheet is not one of this endpoint's options.
            assertThat(stepUp.stepUpMethod).isEqualTo(AccountAuthorityStepUpMethod.Pin)
            assertThat(manager.webStepUpCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * C5's other half: the account-recovery route, authorization 0x02, for an owner with no artifact.
     *
     * It is the weaker route, so it has a screen of its own that says what it costs and an acknowledgement that
     * gates it, and it still hands over a NEW artifact before anything is submitted.
     */
    @Test
    fun `present - the account-recovery route is acknowledged, hands over a new artifact and submits 0x02`() =
        runTest {
            val manager = FakeAccountAuthorityManager(
                stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
                holdsAuthorityResult = { true },
            )
            val presenter = createPresenter(
                manager = manager,
                identityServiceClient = FakeIdentityServiceClient(
                    factorStatusResult = {
                        Result.success(aFactorStatus(hasPin = true, passkeyRegistered = false))
                    },
                ),
            )

            presenter.test {
                val overview = awaitFirst { it.chain != null }
                assertThat(overview.canRecoverThroughAccountRecovery).isTrue()
                overview.eventSink(AccountAuthorityEvent.StartAccountRecovery)

                val notice = awaitFirst { it.phase == AccountAuthorityPhase.AccountRecoveryNotice }
                assertThat(notice.recoveryRoute)
                    .isEqualTo(AccountAuthorityRecoveryRoute.AccountRecovery)
                assertThat(notice.canContinueFromAccountRecoveryNotice).isFalse()
                // The acknowledgement is the gate, not a decoration: continuing without it mints nothing.
                notice.eventSink(AccountAuthorityEvent.ContinueFromAccountRecoveryNotice)
                expectNoEvents()
                assertThat(manager.beginAccountRecoveryCalls).isEmpty()

                notice.eventSink(AccountAuthorityEvent.AcknowledgeAccountRecovery(true))
                awaitFirst { it.accountRecoveryAcknowledged }
                    .eventSink(AccountAuthorityEvent.ContinueFromAccountRecoveryNotice)

                val artifact = awaitFirst { it.phase == AccountAuthorityPhase.Artifact }
                // The NEW artifact this record commits: an owner who came here without one must not leave
                // without one either.
                assertThat(artifact.recoveryArtifact).isNotNull()
                assertThat(artifact.canContinueFromArtifact).isFalse()
                artifact.eventSink(AccountAuthorityEvent.ConfirmArtifactStored(true))
                awaitFirst { it.canContinueFromArtifact }
                    .eventSink(AccountAuthorityEvent.ContinueFromArtifact)
                awaitFirst { it.stepUp == AccountAuthorityStepUp.Recover }
                    .eventSink(AccountAuthorityEvent.PinChanged("123456"))
                awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

                awaitFirst { it.successMessage != null }
                val call = manager.accountRecoveryCalls.single()
                assertThat(call.artifactConfirmed).isTrue()
                assertThat(call.stepUp).isEqualTo(AuthorityStepUp.Pin("123456"))
                // The route is chosen on the way in, so the rank-2 record is not the one that went out.
                assertThat(manager.recoverCalls).isEmpty()
                assertThat(manager.beginRecoveryCalls).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The account-recovery route is refused outright on an account whose id commits its authority, so the offer
     * is withheld and the event does nothing rather than sending a record the server will refuse.
     */
    @Test
    fun `present - a genesis-rooted account is not offered the account-recovery route`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aGenesisRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.chain != null }
            assertThat(state.canRecover).isTrue()
            assertThat(state.canRecoverThroughAccountRecovery).isFalse()
            state.eventSink(AccountAuthorityEvent.StartAccountRecovery)
            expectNoEvents()
            assertThat(manager.beginAccountRecoveryCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Walks the artifact screen and its confirmation, which nothing in this feature may skip. */
    private suspend fun ReceiveTurbine<AccountAuthorityState>.adoptUpToStepUp(
        manager: FakeAccountAuthorityManager,
    ) {
        awaitFirst { it.canAdopt }.eventSink(AccountAuthorityEvent.StartAdoption)
        awaitFirst { it.phase == AccountAuthorityPhase.Artifact }
            .eventSink(AccountAuthorityEvent.ConfirmArtifactStored(true))
        awaitFirst { it.artifactConfirmed }.eventSink(AccountAuthorityEvent.ContinueFromArtifact)
        assertThat(manager.beginAdoptionCalls).hasSize(1)
    }

    private suspend fun AccountAuthorityPresenter.test(
        lifecycleOwner: FakeLifecycleOwner = FakeLifecycleOwner(Lifecycle.State.RESUMED),
        block: suspend ReceiveTurbine<AccountAuthorityState>.() -> Unit,
    ) {
        // The sheet runs in another activity, so this screen reads its return from the lifecycle, exactly as the
        // two-step-verification screen reads its factors back.
        testWithLifecycleOwner(lifecycleOwner) { block() }
    }

    private suspend fun ReceiveTurbine<AccountAuthorityState>.awaitFirst(
        predicate: (AccountAuthorityState) -> Boolean,
    ): AccountAuthorityState {
        repeat(MAX_EMISSIONS) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
        error("No matching state after $MAX_EMISSIONS emissions")
    }

    private fun createPresenter(
        manager: FakeAccountAuthorityManager = FakeAccountAuthorityManager(),
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
        identityServiceClient: FakeIdentityServiceClient = FakeIdentityServiceClient(),
    ) = AccountAuthorityPresenter(
        matrixClient = FakeMatrixClient(sessionId = A_USER_ID),
        sessionStore = sessionStore,
        featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(FeatureFlags.AccountAuthority.key to true),
        ),
        authorityManager = manager,
        identityServiceClient = identityServiceClient,
    )

    private companion object {
        private const val MAX_EMISSIONS = 20
    }
}
