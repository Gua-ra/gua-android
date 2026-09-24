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
import io.element.android.features.preferences.impl.fixtures.A_RECOVERY_ARTIFACT
import io.element.android.features.preferences.impl.fixtures.FakeAccountAuthorityManager
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aBootstrapChain
import io.element.android.features.preferences.impl.fixtures.aCandidate
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.features.preferences.impl.fixtures.aPendingAdoption
import io.element.android.features.preferences.impl.fixtures.aRootedChain
import io.element.android.features.preferences.impl.fixtures.anApproval
import io.element.android.features.preferences.impl.fixtures.anAuthorityDevice
import io.element.android.features.preferences.impl.fixtures.anAuthorityLostChain
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityRecord
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.guaresolver.authority.InvalidAuthorityRecordException
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
                assertThat(call.stepUp).isEqualTo(AuthorityStepUp.Pin("123456"))
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
            // The copy names the two factors the account's own step-up accepts, the passkey and the PIN. There
            // is no branch in this screen that falls back to a code sent to the phone.
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

    /**
     * The whole point of C3: the lifecycle is not adoption alone.
     *
     * A rooted account's screen offers the transitions the chain actually permits, each one drawn in the
     * chain's own terms, and offers no second adoption.
     */
    @Test
    fun `present - a rooted account can grant, revoke and recover, and cannot adopt again`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.chain != null }
            assertThat(state.canAdopt).isFalse()
            assertThat(state.canRecover).isTrue()
            assertThat(state.candidates).hasSize(1)
            assertThat(state.devices).hasSize(1)
            assertThat(state.thisDeviceKeyB64Url).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a grant needs the fingerprint compared, and carries that it was`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.candidates.isNotEmpty() }
                .eventSink(AccountAuthorityEvent.SelectCandidate(aCandidate().deviceKeyB64Url))
            val compare = awaitFirst { it.phase == AccountAuthorityPhase.Compare }
            assertThat(compare.canContinueFromCompare).isFalse()
            // The comparison is the gate: continuing without it changes nothing and sends nothing.
            compare.eventSink(AccountAuthorityEvent.ContinueFromCompare)
            compare.eventSink(AccountAuthorityEvent.ConfirmFingerprint(true))
            awaitFirst { it.canContinueFromCompare }
                .eventSink(AccountAuthorityEvent.ContinueFromCompare)
            awaitFirst { it.phase == AccountAuthorityPhase.StepUp && it.stepUp == AccountAuthorityStepUp.Grant }
                .eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            awaitFirst { it.successMessage != null }
            val call = manager.grantCalls.single()
            assertThat(call.candidate.deviceKeyB64Url).isEqualTo(aCandidate().deviceKeyB64Url)
            assertThat(call.fingerprintConfirmed).isTrue()
            assertThat(call.stepUp).isEqualTo(AuthorityStepUp.Pin("123456"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - revoking this device is a different transition from revoking another`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = {
                Result.success(
                    aRootedChain(
                        listOf(
                            anAuthorityDevice(deviceKeyB64Url = A_DEVICE_KEY),
                            anAuthorityDevice(deviceKeyB64Url = "another-device-key", label = "Pixel Tablet"),
                        )
                    )
                )
            },
            holdsAuthorityResult = { true },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.devices.size == 2 }
                .eventSink(AccountAuthorityEvent.StartRevocation("another-device-key"))
            val stepUp = awaitFirst { it.revocationTarget != null && it.phase == AccountAuthorityPhase.StepUp }
            assertThat(stepUp.revocationTarget?.isThisDevice).isFalse()
            // Two active devices, so the one being removed may object: decision 5's carve-out, which the copy
            // has to state before the owner starts a standoff.
            assertThat(stepUp.revocationTarget?.targetMayObject).isTrue()
            stepUp.eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            awaitFirst { it.successMessage != null }
            val call = manager.revokeCalls.single()
            assertThat(call.deviceKeyB64Url).isEqualTo("another-device-key")
            assertThat(call.reason).isEqualTo(AuthorityRecord.REASON_UNSPECIFIED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a device that holds authority objects with a record, not with its session`() = runTest {
        val pending = aPendingAdoption(type = "DEVICE_REVOKE", seq = 3)
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()), pending = pending)) },
            holdsAuthorityResult = { true },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.pendingTransition != null }.eventSink(AccountAuthorityEvent.Oppose)

            awaitFirst { it.successMessage != null }
            // The signed record, because a session's word is only accepted against an adoption: a stolen
            // bearer token must not be able to veto the owner's own revocation of the thief's device.
            assertThat(manager.opposeRecordCalls).hasSize(1)
            assertThat(manager.opposeCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a recovery validates the artifact locally and shows the new one once`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.chain != null }.eventSink(AccountAuthorityEvent.StartRecovery)
            awaitFirst { it.phase == AccountAuthorityPhase.RecoveryEntry }
                .eventSink(AccountAuthorityEvent.RecoveryArtifactChanged(A_RECOVERY_ARTIFACT))
            awaitFirst { it.canContinueFromRecoveryEntry }
                .eventSink(AccountAuthorityEvent.ContinueFromRecoveryEntry)
            val artifact = awaitFirst { it.phase == AccountAuthorityPhase.Artifact }
            // The NEW artifact this record commits, shown once and confirmed, exactly as adoption does.
            assertThat(artifact.recoveryArtifact).isNotNull()
            assertThat(artifact.canContinueFromArtifact).isFalse()
            artifact.eventSink(AccountAuthorityEvent.ConfirmArtifactStored(true))
            awaitFirst { it.canContinueFromArtifact }
                .eventSink(AccountAuthorityEvent.ContinueFromArtifact)
            awaitFirst { it.stepUp == AccountAuthorityStepUp.Recover }
                .eventSink(AccountAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(AccountAuthorityEvent.Submit)

            awaitFirst { it.successMessage != null }
            assertThat(manager.beginRecoveryCalls).containsExactly(A_RECOVERY_ARTIFACT)
            val call = manager.recoverCalls.single()
            assertThat(call.artifact).isEqualTo(A_RECOVERY_ARTIFACT)
            assertThat(call.artifactConfirmed).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - malformed recovery material is refused on this side, with what is wrong`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { true },
            beginRecoveryResult = {
                Result.failure(
                    InvalidAuthorityRecordException("bad_artifact_prefix", "a recovery key starts with the prefix")
                )
            },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitFirst { it.chain != null }.eventSink(AccountAuthorityEvent.StartRecovery)
            awaitFirst { it.phase == AccountAuthorityPhase.RecoveryEntry }
                .eventSink(AccountAuthorityEvent.RecoveryArtifactChanged("please let me in"))
            awaitFirst { it.canContinueFromRecoveryEntry }
                .eventSink(AccountAuthorityEvent.ContinueFromRecoveryEntry)

            val refused = awaitFirst { it.recoveryArtifactError != null }
            assertThat(refused.recoveryArtifactError)
                .isEqualTo(R.string.screen_account_authority_recovery_artifact_wrong_kind)
            assertThat(refused.phase).isEqualTo(AccountAuthorityPhase.RecoveryEntry)
            // Nothing was submitted, so nothing was spent: no challenge and no step-up for a typo.
            assertThat(manager.recoverCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - an account with no factor at all is told that, and is not offered a PIN field`() = runTest {
        val presenter = createPresenter(
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = {
                    Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false))
                },
            ),
        )

        presenter.test {
            awaitFirst { it.canAdopt }.eventSink(AccountAuthorityEvent.StartAdoption)
            awaitFirst { it.phase == AccountAuthorityPhase.Artifact }
                .eventSink(AccountAuthorityEvent.ConfirmArtifactStored(true))
            awaitFirst { it.canContinueFromArtifact }
                .eventSink(AccountAuthorityEvent.ContinueFromArtifact)

            assertThat(awaitFirst { it.stepUpBlock != null }.stepUpBlock)
                .isEqualTo(AccountAuthorityStepUpBlock.NoFactorRegistered)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The terminal state of decision 7: no adoption, no recovery, and copy that says why. */
    @Test
    fun `present - an account that lost its authority is not offered a way back`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(anAuthorityLostChain()) },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.chain != null }
            assertThat(state.authorityLost).isTrue()
            assertThat(state.canAdopt).isFalse()
            // The artifact was the way back, and this account has neither it nor a device.
            state.eventSink(AccountAuthorityEvent.StartAdoption)
            assertThat(manager.adoptCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a device with no authority offers its own key and shows its fingerprint`() = runTest {
        val manager = FakeAccountAuthorityManager(
            stateResult = { Result.success(aRootedChain(listOf(anAuthorityDevice()))) },
            holdsAuthorityResult = { false },
        )
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitFirst { it.chain != null }
            assertThat(state.canOfferThisDevice).isTrue()
            // A device with no authority is not shown other devices' candidates: it could do nothing with
            // them, and the request would be answered for nothing.
            assertThat(state.candidates).isEmpty()
            state.eventSink(AccountAuthorityEvent.OfferThisDevice)

            val offered = awaitFirst { it.thisDeviceFingerprint != null }
            assertThat(offered.thisDeviceFingerprint).isEqualTo("9KDC ZT8A")
            cancelAndIgnoreRemainingEvents()
        }
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
        featureEnabled: Boolean = true,
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
        identityServiceClient: FakeIdentityServiceClient = FakeIdentityServiceClient(),
    ) = AccountAuthorityPresenter(
        matrixClient = FakeMatrixClient(sessionId = A_USER_ID),
        sessionStore = sessionStore,
        featureFlagService = FakeFeatureFlagService(
            initialState = mapOf(FeatureFlags.AccountAuthority.key to featureEnabled),
        ),
        authorityManager = manager,
        identityServiceClient = identityServiceClient,
    )

    private companion object {
        private const val MAX_EMISSIONS = 20
    }
}
