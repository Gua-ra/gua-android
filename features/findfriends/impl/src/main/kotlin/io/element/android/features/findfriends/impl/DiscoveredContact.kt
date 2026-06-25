/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import io.element.android.libraries.matrix.api.core.UserId

/**
 * GUA FORK: a device address-book contact that has been matched to a Gua account. Android
 * counterpart of iOS `DiscoveredContact`.
 *
 * [localName] is how the user knows the person (their address-book name), falling back to the Gua
 * display name. [handle] is the homeserver-abstracted global handle and is the only id ever shown.
 */
data class DiscoveredContact(
    val localName: String,
    val userId: UserId,
    /** Homeserver-abstracted handle (e.g. `@alice`) — never surfaces the homeserver. */
    val handle: String,
    val avatarUrl: String?,
)
