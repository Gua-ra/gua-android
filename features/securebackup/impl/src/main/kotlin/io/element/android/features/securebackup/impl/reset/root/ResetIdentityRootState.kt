/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset.root

data class ResetIdentityRootState(
    val displayConfirmationDialog: Boolean,
    /**
     * GUA FORK: true only when another device of this account holds the keys, so verifying with
     * it can bring the messages here without a reset. Otherwise the reset is the only option.
     */
    val canRecoverFromOtherDevice: Boolean,
    val eventSink: (ResetIdentityRootEvent) -> Unit,
)
