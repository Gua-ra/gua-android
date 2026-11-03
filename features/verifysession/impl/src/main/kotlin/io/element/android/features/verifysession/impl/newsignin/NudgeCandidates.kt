/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.verifysession.impl.newsignin

import io.element.android.libraries.matrix.api.core.DeviceId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal val RECENTLY_SEEN = 10.minutes
internal val RECENT_REQUEST = 10.minutes
internal val NUDGE_COOLDOWN = 120.seconds
internal const val MAX_NUDGES_PER_DEVICE = 3

internal data class NudgeHistory(
    val count: Int,
    val lastAtMillis: Long,
)

internal fun selectNudgeCandidate(
    ownDeviceId: DeviceId,
    devices: List<OwnDevice>,
    keys: OwnDeviceKeys,
    nowMillis: Long,
    dismissed: Set<DeviceId>,
    nudges: Map<DeviceId, NudgeHistory>,
    requestsReceivedAtMillis: Map<DeviceId, Long>,
): DeviceId? {
    if (keys.selfSigningKeyIds.isEmpty()) return null
    val lastSeen = devices.associate { it.deviceId to it.lastSeenTs }
    return keys.devices
        .filter { (deviceId, info) ->
            deviceId != ownDeviceId &&
                !info.isDehydrated &&
                info.ownSignatureKeyIds.none { it in keys.selfSigningKeyIds } &&
                deviceId !in dismissed
        }
        .mapNotNull { (deviceId, _) ->
            val lastSeenTs = lastSeen[deviceId] ?: return@mapNotNull null
            deviceId.takeIf {
                nowMillis - lastSeenTs <= RECENTLY_SEEN.inWholeMilliseconds &&
                    nudges[deviceId].allowsAnotherNudge(nowMillis) &&
                    requestsReceivedAtMillis[deviceId]?.let { nowMillis - it < RECENT_REQUEST.inWholeMilliseconds } != true
            }?.let { it to lastSeenTs }
        }
        .maxByOrNull { (_, lastSeenTs) -> lastSeenTs }
        ?.first
}

private fun NudgeHistory?.allowsAnotherNudge(nowMillis: Long): Boolean =
    this == null || count < MAX_NUDGES_PER_DEVICE && nowMillis - lastAtMillis >= NUDGE_COOLDOWN.inWholeMilliseconds
