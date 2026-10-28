/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.space.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Spaces section is shown. Stored in account data so it follows the account to every phone.
 */
interface SpacesSwitch {
    val isEnabled: StateFlow<Boolean>

    suspend fun setEnabled(enabled: Boolean): Result<Unit>
}
