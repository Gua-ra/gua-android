/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: the account two-step-verification (PIN) status, as returned by
 * `GET /security/pin/status`. Android counterpart of the iOS `PinStatus` value.
 *
 * [changePhoneCooldownRemainingSeconds] is the WhatsApp-style fresh-2FA cooldown: after a PIN is
 * created, changed or reset, the change-phone flow is held for a window (default 7 days) so a
 * SIM-swapper who just set a PIN cannot immediately use it to take over the number. `0` means no
 * active cooldown.
 */
data class PinStatus(
    val hasPin: Boolean,
    val changePhoneCooldownRemainingSeconds: Long,
)
