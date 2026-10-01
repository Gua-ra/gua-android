/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

data class ContactMatch(
    val hashedPhone: String,
    val userId: String,
    val displayHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
