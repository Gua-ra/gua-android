/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.observer.FakeSessionObserver
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class DefaultDismissedSignInStoreTest : RobolectricTest() {
    private val sessionObserver = FakeSessionObserver()

    @Test
    fun `dismissed devices are kept per account`() = runTest {
        val alice = createStore(ALICE)
        val bob = createStore(BOB)

        alice.dismiss(DeviceId("WEB1"))
        alice.dismiss(DeviceId("WEB2"))

        assertThat(alice.dismissedDevices()).containsExactly(DeviceId("WEB1"), DeviceId("WEB2"))
        assertThat(createStore(ALICE).dismissedDevices()).containsExactly(DeviceId("WEB1"), DeviceId("WEB2"))
        assertThat(bob.dismissedDevices()).isEmpty()
    }

    @Test
    fun `signing out clears that account's dismissed devices only`() = runTest {
        val alice = createStore(ALICE)
        val bob = createStore(BOB)
        alice.dismiss(DeviceId("WEB1"))
        bob.dismiss(DeviceId("WEB2"))

        sessionObserver.onSessionDeleted(ALICE.value)

        assertThat(alice.dismissedDevices()).isEmpty()
        assertThat(bob.dismissedDevices()).containsExactly(DeviceId("WEB2"))
    }

    private fun TestScope.createStore(sessionId: SessionId) = DefaultDismissedSignInStore(
        context = RuntimeEnvironment.getApplication(),
        matrixClient = FakeMatrixClient(sessionId = sessionId),
        sessionObserver = sessionObserver,
        dispatchers = testCoroutineDispatchers(),
    )

    private companion object {
        val ALICE = SessionId("@alice:server.org")
        val BOB = SessionId("@bob:server.org")
    }
}
