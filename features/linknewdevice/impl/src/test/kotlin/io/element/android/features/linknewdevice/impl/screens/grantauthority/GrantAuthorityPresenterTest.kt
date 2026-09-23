/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.linknewdevice.impl.R
import io.element.android.libraries.guaresolver.authority.AuthorityError
import io.element.android.libraries.guaresolver.authority.AuthorityStepUp
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: the grant offer that follows a link in the one direction ADM-009 decision 5 permits.
 *
 * Declining is a first-class outcome, and the test for it asserts silence: a device that was linked and not
 * granted is a working, signed-in device, and nothing about the account's chain changed.
 */
class GrantAuthorityPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - the offer names the new device and sends nothing on its own`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(manager = manager)

        presenter.test {
            val state = awaitItem()
            assertThat(state.phase).isEqualTo(GrantAuthorityPhase.Prompt)
            assertThat(state.deviceLabel).isEqualTo("Pixel Tablet")
            assertThat(manager.grantCalls).isEmpty()
            assertThat(manager.stateCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - declining finishes the flow and grants nothing`() = runTest {
        val manager = FakeAccountAuthorityManager()
        var done = false
        val presenter = createPresenter(manager = manager, onDone = { done = true })

        presenter.test {
            awaitItem().eventSink(GrantAuthorityEvent.Skip)
            awaitFirst { it.phase == GrantAuthorityPhase.Done }
            assertThat(done).isTrue()
            assertThat(manager.grantCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - granting reads the head first, then signs over the new device's own key`() = runTest {
        val manager = FakeAccountAuthorityManager()
        var done = false
        val presenter = createPresenter(manager = manager, onDone = { done = true })

        presenter.test {
            awaitItem().eventSink(GrantAuthorityEvent.Grant)
            val compare = awaitFirst { it.phase == GrantAuthorityPhase.Compare }
            // The fingerprint is shown in the two groups a person reads out.
            assertThat(compare.fingerprint).isEqualTo("9KDC ZT8A")
            assertThat(compare.canContinueFromCompare).isFalse()
            compare.eventSink(GrantAuthorityEvent.ConfirmFingerprint(true))
            awaitFirst { it.canContinueFromCompare }
                .eventSink(GrantAuthorityEvent.ContinueFromCompare)
            awaitFirst { it.phase == GrantAuthorityPhase.StepUp }
                .eventSink(GrantAuthorityEvent.PinChanged("123456"))
            val ready = awaitFirst { it.pin == "123456" }
            assertThat(ready.canSubmit).isTrue()
            ready.eventSink(GrantAuthorityEvent.Submit)

            awaitFirst { it.phase == GrantAuthorityPhase.Done }
            // The chain is read every time, because a head this screen remembered from before the link
            // would be refused.
            assertThat(manager.stateCalls).hasSize(1)
            val call = manager.grantCalls.single()
            assertThat(call.candidate.deviceKeyB64Url).isEqualTo(A_GRANTEE_KEY)
            assertThat(call.candidate.label).isEqualTo("Pixel Tablet")
            assertThat(call.stepUp).isEqualTo(AuthorityStepUp.Pin("123456"))
            assertThat(call.fingerprintConfirmed).isTrue()
            assertThat(done).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The comparison is the binding, so the screen has to be unable to skip it.
     *
     * The check code that got the user here bound the channel; it says nothing about which key came up it. A
     * grant that skipped the fingerprint would be a grant over whatever arrived.
     */
    @Test
    fun `present - the step-up is unreachable until the fingerprint was compared`() = runTest {
        val manager = FakeAccountAuthorityManager()
        val presenter = createPresenter(manager = manager)

        presenter.test {
            awaitItem().eventSink(GrantAuthorityEvent.Grant)
            val compare = awaitFirst { it.phase == GrantAuthorityPhase.Compare }
            compare.eventSink(GrantAuthorityEvent.ContinueFromCompare)
            runCurrent()

            // Nothing moved, so there is nothing new to emit, and nothing was sent.
            expectNoEvents()
            assertThat(manager.grantCalls).isEmpty()
            assertThat(manager.stateCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a refusal keeps the user on the step-up with the rule that refused it`() = runTest {
        val manager = FakeAccountAuthorityManager(
            grantResult = { Result.failure(AuthorityError.DeviceQuarantined) },
        )
        var done = false
        val presenter = createPresenter(manager = manager, onDone = { done = true })

        presenter.test {
            awaitItem().eventSink(GrantAuthorityEvent.Grant)
            awaitFirst { it.phase == GrantAuthorityPhase.Compare }
                .eventSink(GrantAuthorityEvent.ConfirmFingerprint(true))
            awaitFirst { it.canContinueFromCompare }
                .eventSink(GrantAuthorityEvent.ContinueFromCompare)
            awaitFirst { it.phase == GrantAuthorityPhase.StepUp }
                .eventSink(GrantAuthorityEvent.PinChanged("123456"))
            awaitFirst { it.canSubmit }.eventSink(GrantAuthorityEvent.Submit)

            val refused = awaitFirst { it.errorMessage != null }
            assertThat(refused.errorMessage)
                .isEqualTo(R.string.screen_link_grant_authority_error_quarantined)
            assertThat(refused.phase).isEqualTo(GrantAuthorityPhase.StepUp)
            // The PIN is cleared rather than left on screen for a second attempt to reuse.
            assertThat(refused.pin).isEmpty()
            assertThat(done).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - the PIN field takes digits only and never more than six`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            awaitItem().eventSink(GrantAuthorityEvent.Grant)
            awaitFirst { it.phase == GrantAuthorityPhase.Compare }
                .eventSink(GrantAuthorityEvent.ConfirmFingerprint(true))
            awaitFirst { it.canContinueFromCompare }
                .eventSink(GrantAuthorityEvent.ContinueFromCompare)
            awaitFirst { it.phase == GrantAuthorityPhase.StepUp }
                .eventSink(GrantAuthorityEvent.PinChanged("12a34567890"))
            val state = awaitFirst { it.pin.isNotEmpty() }
            assertThat(state.pin).isEqualTo("123456")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<GrantAuthorityState>.awaitFirst(
        predicate: (GrantAuthorityState) -> Boolean,
    ): GrantAuthorityState {
        repeat(MAX_EMISSIONS) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
        error("No matching state after $MAX_EMISSIONS emissions")
    }

    private fun createPresenter(
        manager: FakeAccountAuthorityManager = FakeAccountAuthorityManager(),
        onDone: () -> Unit = {},
        sessionStore: SessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_USER_ID.value))),
    ) = GrantAuthorityPresenter(
        candidate = aCandidate(deviceKeyB64Url = A_GRANTEE_KEY, fingerprint = A_FINGERPRINT),
        onDone = onDone,
        sessionId = A_USER_ID,
        sessionStore = sessionStore,
        authorityManager = manager,
    )

    private companion object {
        private const val MAX_EMISSIONS = 20
        private const val A_GRANTEE_KEY = "a-new-device-key"
        private const val A_FINGERPRINT = "9KDCZT8A"
    }
}
