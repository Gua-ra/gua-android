/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.fixtures.FakeAccountAuthorityManager
import io.element.android.features.preferences.impl.fixtures.aBootstrapChain
import io.element.android.features.preferences.impl.fixtures.aPendingAdoption
import io.element.android.features.preferences.impl.fixtures.aRootedChain
import io.element.android.features.preferences.impl.fixtures.anApproval
import io.element.android.features.preferences.impl.fixtures.anAuthorityDevice
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: the account authority screen (ADM-009).
 *
 * The first test is the one the whole feature ships behind: with the flag off nothing is read, nothing is
 * requested, and no event does anything. The rest hold the order decision 4 fixes, which is the security of
 * the transition: keys, then the artifact and its confirmation, then the step-up, then the record.
 */
class AccountAuthorityPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - with the flag off nothing is read and no event reaches the chain`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(manager = manager, featureEnabled = false)

        presenter.test {
            val state = awaitFirst { it.phase == AccountAuthorityPhase.Overview }
            assertThat(state.featureEnabled).isFalse()
            assertThat(state.chain).isNull()
            state.eventSink(AccountAuthorityEvent.StartAdoption)
            state.eventSink(AccountAuthorityEvent.Refresh)
            // Not "an empty chain" and not an error: the feature is simply not there, and nothing about the
            // account was asked for.
            assertThat(manager.stateCalls).isEmpty()
            assertThat(manager.beginAdoptionCalls).isEmpty()
            assertThat(manager.adoptCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a deployment with the feature off is not an error`() = runTest {
        val manager = FakeAccountAuthorityManager(stateResult = { Result.failure(AuthorityError.Disabled) })
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.phase == AccountAuthorityPhase.Overview }
            assertThat(state.unavailable).isTrue()
            assertThat(state.errorMessage).isNull()
            assertThat(state.canAdopt).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a bootstrap account with an empty chain can adopt`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val state = awaitFirst { it.chain != null }
            assertThat(state.canAdopt).isTrue()
            assertThat(state.devices).isEmpty()
            assertThat(state.unavailable).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - adoption shows the artifact once and is unreachable until it is confirmed`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.canAdopt }.eventSink(AccountAuthorityEvent.StartAdoption)
            val artifact = awaitFirst { it.phase == AccountAuthorityPhase.Artifact }
            assertThat(artifact.recoveryArtifact).isNotNull()
            assertThat(artifact.canContinueFromArtifact).isFalse()

            // The unconfirmed continue is refused rather than being merely disabled on screen.
            artifact.eventSink(AccountAuthorityEvent.ContinueFromArtifact)
            expectNoEvents()
            artifact.eventSink(AccountAuthorityEvent.ConfirmArtifactStored(true))
            val confirmed = awaitFirst { it.artifactConfirmed }
            assertThat(confirmed.phase).isEqualTo(AccountAuthorityPhase.Artifact)
            assertThat(confirmed.canContinueFromArtifact).isTrue()

            confirmed.eventSink(AccountAuthorityEvent.ContinueFromArtifact)
            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            assertThat(stepUp.stepUp).isEqualTo(AccountAuthorityStepUp.Adopt)
            assertThat(manager.adoptCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - the adoption is submitted with the PIN and the confirmation, and never with a code`() =
        runTest {
            val manager = FakeAccountAuthorityManager()
            val presenter = createPresenter(manager = manager)

            presenter.test {
                adoptUpToStepUp(manager)
                awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                    .eventSink(AccountAuthorityEvent.PinChanged("123456"))
                val ready = awaitFirst { it.pin == "123456" }
                assertThat(ready.canSubmit).isTrue()
                ready.eventSink(AccountAuthorityEvent.Submit)

                val done = awaitFirst { it.successMessage != null }
                assertThat(done.successMessage).isEqualTo(R.string.screen_account_authority_submitted)
                // The artifact is dropped once the record is in: it is a private key, and the screen that
                // showed it has done its job.
                assertThat(done.recoveryArtifact).isNull()
                val call = manager.adoptCalls.single()
                assertThat(call.pin).isEqualTo("123456")
                assertThat(call.artifactConfirmed).isTrue()
                assertThat(call.deviceLabel).isNotEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `present - a step-up the account cannot produce is named rather than worked around`() = runTest {
        val manager = FakeAccountAuthorityManager(
            adoptResult = { Result.failure(AuthorityError.StepUpRequired) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            adoptUpToStepUp(manager)
            awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                .eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            val refused = awaitFirst { it.errorMessage != null }
            // The copy says the account's own factors and says the passkey cannot be used here. There is no
            // branch in this screen that falls back to a code sent to the phone.
            assertThat(refused.errorMessage)
                .isEqualTo(R.string.screen_account_authority_error_step_up_required)
            assertThat(refused.phase).isEqualTo(AccountAuthorityPhase.StepUp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a recent account recovery refuses the adoption in its own words`() = runTest {
        val manager = FakeAccountAuthorityManager(
            adoptResult = { Result.failure(AuthorityError.RecoveryTooRecent) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            adoptUpToStepUp(manager)
            awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
                .eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            assertThat(awaitFirst { it.errorMessage != null }.errorMessage)
                .isEqualTo(R.string.screen_account_authority_error_recovery_too_recent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a quarantined device and a pending transition are reported as they are`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = {
                Result.success(
                    aRootedChain(
                        devices = listOf(
                            anAuthorityDevice(label = "Pixel 9"),
                            anAuthorityDevice(
                                label = "Tablet",
                                state = "QUARANTINED",
                                quarantineUntilEpochSeconds = 1_800_000_000,
                            ),
                        )
                    )
                )
            },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.devices.isNotEmpty() }
            assertThat(state.devices).hasSize(2)
            assertThat(state.devices[0].isActive).isTrue()
            // A quarantined device can do nothing and counts for nothing, so it is never drawn as active.
            assertThat(state.devices[1].isQuarantined).isTrue()
            assertThat(state.devices[1].isActive).isFalse()
            assertThat(state.canAdopt).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - the first opposition needs no factor and the second asks for one`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aBootstrapChain(pending = aPendingAdoption())) },
            opposeResult = { Result.failure(AuthorityError.StepUpRequired) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val pending = awaitFirst { it.pendingTransition != null }
            assertThat(pending.pendingTransition?.effectiveAtEpochSeconds).isEqualTo(1_800_000_000)
            pending.eventSink(AccountAuthorityEvent.Oppose)

            // The first attempt carries no factor at all, which is what makes the honest veto cheap.
            val stepUp = awaitFirst { it.phase == AccountAuthorityPhase.StepUp }
            assertThat(manager.opposeCalls.single()).isEqualTo("a-record-hash" to null)
            assertThat(stepUp.stepUp).isEqualTo(AccountAuthorityStepUp.Oppose)
            // And the fresh-factor hold never gates an objection, so no age is asked about here.
            assertThat(stepUp.errorMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - an approval is only offered when this device can actually sign one`() = runTest {
        val withoutAuthority = FakeAccountAuthorityManager(
            holdsAuthorityResult = { false },
            approvalsResult = { Result.success(listOf(anApproval())) },
        )

        createPresenter(manager = withoutAuthority).test {
            val state = awaitFirst { it.phase == AccountAuthorityPhase.Overview }
            assertThat(state.approvals).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        val withAuthority = FakeAccountAuthorityManager(
            holdsAuthorityResult = { true },
            stateResult = { Result.success(aRootedChain(devices = listOf(anAuthorityDevice()))) },
            approvalsResult = { Result.success(listOf(anApproval(approvalId = "approval-1"))) },
        )

        createPresenter(manager = withAuthority).test {
            val state = awaitFirst { it.approvals.isNotEmpty() }
            assertThat(state.approvals.single().code).isEqualTo("AB7K")
            state.eventSink(AccountAuthorityEvent.Approve("approval-1"))
            awaitFirst { it.successMessage != null }
            assertThat(withAuthority.approveCalls).containsExactly("approval-1")
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
        featureEnabled: Boolean = true,
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
    ) = AccountAuthorityPresenter(
        matrixClient = FakeMatrixClient(sessionId = A_USER_ID),
        sessionStore = sessionStore,
        featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(FeatureFlags.AccountAuthority.key to featureEnabled),
        ),
        authorityManager = manager,
    )

    private companion object {
        private const val MAX_EMISSIONS = 20
    }
}
