/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import io.element.android.libraries.matrix.api.core.UserId

data class DiscoveredContact(
    val localName: String,
    val userId: UserId,
    val handle: String,
    val avatarUrl: String?,
)
