/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import io.element.android.libraries.matrix.api.core.UserId

/**
 * A device address-book contact matched to a Gua account. [localName] is how the user knows the
 * person (their address-book name), falling back to the Gua display name. [handle] is the
 * homeserver-abstracted global handle and the only id ever shown.
 */
data class DiscoveredContact(
    val localName: String,
    val userId: UserId,
    val handle: String,
    val avatarUrl: String?,
)
