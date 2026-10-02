/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class DefaultIdentityResetPendingStoreTest : RobolectricTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a marker written by this device is pending until cleared`() {
        val store = createStore(DeviceId("DEVICE_A"))

        assertThat(store.isPending()).isFalse()
        store.markPending()
        assertThat(store.isPending()).isTrue()
        store.clear()
        assertThat(store.isPending()).isFalse()
    }

    @Test
    fun `a marker left by an earlier device of the same account is not pending`() {
        val earlierDevice = createStore(DeviceId("DEVICE_A"))
        earlierDevice.markPending()

        val newLogin = createStore(DeviceId("DEVICE_B"))

        assertThat(newLogin.isPending()).isFalse()
    }

    @Test
    fun `the earlier device-less marker is ignored and removed on the next write`() {
        val preferences = context.getSharedPreferences("gua_identity_reset", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("pending_" + FakeMatrixClient().sessionId.value, true).commit()
        val store = createStore(DeviceId("DEVICE_A"))

        assertThat(store.isPending()).isFalse()

        store.markPending()
        assertThat(preferences.contains("pending_" + FakeMatrixClient().sessionId.value)).isFalse()
        assertThat(store.isPending()).isTrue()
    }

    private fun createStore(deviceId: DeviceId) = DefaultIdentityResetPendingStore(
        context = context,
        matrixClient = FakeMatrixClient(deviceId = deviceId),
    )
}
