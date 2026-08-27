/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

// GUA FORK: "Learn more" help links point at gua.global instead of element.io/help.
// The gua.global/help anchors below must be published before a public-facing beta.
object LearnMoreConfig {
    const val ENCRYPTION_URL: String = "https://gua.global/help#encryption"
    const val DEVICE_VERIFICATION_URL: String = "https://gua.global/help#device-verification"
    const val SECURE_BACKUP_URL: String = "https://gua.global/help#key-backup"
    const val IDENTITY_CHANGE_URL: String = "https://gua.global/help#identity-change"
    const val HISTORY_VISIBLE_URL: String = "https://gua.global/help#history-sharing"
}
