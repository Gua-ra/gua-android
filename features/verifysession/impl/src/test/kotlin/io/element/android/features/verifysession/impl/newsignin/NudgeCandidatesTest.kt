/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.DeviceId
import org.junit.Test

class NudgeCandidatesTest {
    @Test
    fun `an unsigned, recently seen device of this account is the candidate`() {
        assertThat(select()).isEqualTo(NEW_DEVICE)
    }

    @Test
    fun `a device signed by the current self-signing key is not a candidate`() {
        val keys = keysOf(NEW_DEVICE to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf("ed25519:$NEW_DEVICE_ID", SSK_ID), isDehydrated = false))

        assertThat(select(keys = keys)).isNull()
    }

    @Test
    fun `a device signed only by an older self-signing key is a candidate`() {
        val keys = keysOf(NEW_DEVICE to OwnDeviceKeyInfo(ownSignatureKeyIds = setOf("ed25519:old-ssk"), isDehydrated = false))

        assertThat(select(keys = keys)).isEqualTo(NEW_DEVICE)
    }

    @Test
    fun `without a self-signing key there is no candidate`() {
        assertThat(select(keys = keysOf(NEW_DEVICE to unsigned()).copy(selfSigningKeyIds = emptySet()))).isNull()
    }

    @Test
    fun `a dehydrated device is not a candidate`() {
        val keys = keysOf(NEW_DEVICE to OwnDeviceKeyInfo(ownSignatureKeyIds = emptySet(), isDehydrated = true))

        assertThat(select(keys = keys)).isNull()
    }

    @Test
    fun `this device is never a candidate`() {
        assertThat(select(keys = keysOf(OWN_DEVICE to unsigned()), devices = listOf(OwnDevice(OWN_DEVICE, NOW)))).isNull()
    }

    @Test
    fun `a device missing from the keys query is not a candidate`() {
        assertThat(select(keys = keysOf())).isNull()
    }

    @Test
    fun `last seen up to 10 minutes ago is recent, earlier or unknown is not`() {
        assertThat(select(devices = listOf(OwnDevice(NEW_DEVICE, NOW - TEN_MINUTES)))).isEqualTo(NEW_DEVICE)
        assertThat(select(devices = listOf(OwnDevice(NEW_DEVICE, NOW - TEN_MINUTES - 1)))).isNull()
        assertThat(select(devices = listOf(OwnDevice(NEW_DEVICE, null)))).isNull()
        assertThat(select(devices = emptyList())).isNull()
    }

    @Test
    fun `a dismissed device is not a candidate`() {
        assertThat(select(dismissed = setOf(NEW_DEVICE))).isNull()
    }

    @Test
    fun `a device nudged in the last 120 seconds is not a candidate`() {
        assertThat(select(nudges = mapOf(NEW_DEVICE to NudgeHistory(count = 1, lastAtMillis = NOW - 119_999)))).isNull()
        assertThat(select(nudges = mapOf(NEW_DEVICE to NudgeHistory(count = 1, lastAtMillis = NOW - 120_000)))).isEqualTo(NEW_DEVICE)
    }

    @Test
    fun `a device nudged 3 times is not a candidate again`() {
        assertThat(select(nudges = mapOf(NEW_DEVICE to NudgeHistory(count = 2, lastAtMillis = NOW - TEN_MINUTES)))).isEqualTo(NEW_DEVICE)
        assertThat(select(nudges = mapOf(NEW_DEVICE to NudgeHistory(count = 3, lastAtMillis = NOW - TEN_MINUTES)))).isNull()
    }

    @Test
    fun `a device that sent a verification request in the last 10 minutes is not a candidate`() {
        assertThat(select(requests = mapOf(NEW_DEVICE to NOW - TEN_MINUTES + 1))).isNull()
        assertThat(select(requests = mapOf(NEW_DEVICE to NOW - TEN_MINUTES))).isEqualTo(NEW_DEVICE)
    }

    @Test
    fun `the most recently seen candidate wins`() {
        val other = DeviceId("OTHER")
        val keys = keysOf(NEW_DEVICE to unsigned(), other to unsigned())
        val devices = listOf(OwnDevice(NEW_DEVICE, NOW - 60_000), OwnDevice(other, NOW - 1_000))

        assertThat(select(keys = keys, devices = devices)).isEqualTo(other)
        assertThat(select(keys = keys, devices = devices, dismissed = setOf(other))).isEqualTo(NEW_DEVICE)
    }

    private fun select(
        keys: OwnDeviceKeys = keysOf(OWN_DEVICE to signed(), NEW_DEVICE to unsigned()),
        devices: List<OwnDevice> = listOf(OwnDevice(OWN_DEVICE, NOW), OwnDevice(NEW_DEVICE, NOW - 60_000)),
        dismissed: Set<DeviceId> = emptySet(),
        nudges: Map<DeviceId, NudgeHistory> = emptyMap(),
        requests: Map<DeviceId, Long> = emptyMap(),
    ) = selectNudgeCandidate(
        ownDeviceId = OWN_DEVICE,
        devices = devices,
        keys = keys,
        nowMillis = NOW,
        dismissed = dismissed,
        nudges = nudges,
        requestsReceivedAtMillis = requests,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val TEN_MINUTES = 600_000L
        const val SSK_ID = "ed25519:ssk-public-key"
        const val NEW_DEVICE_ID = "NEWDEVICE"
        val OWN_DEVICE = DeviceId("OWNDEVICE")
        val NEW_DEVICE = DeviceId(NEW_DEVICE_ID)

        fun signed() = OwnDeviceKeyInfo(ownSignatureKeyIds = setOf(SSK_ID), isDehydrated = false)

        fun unsigned() = OwnDeviceKeyInfo(ownSignatureKeyIds = emptySet(), isDehydrated = false)

        fun keysOf(vararg devices: Pair<DeviceId, OwnDeviceKeyInfo>) = OwnDeviceKeys(selfSigningKeyIds = setOf(SSK_ID), devices = devices.toMap())
    }
}
