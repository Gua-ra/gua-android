/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: a contact-discovery hit — a hashed address-book phone number that belongs to a Gua
 * account. Android counterpart of iOS `ContactMatch`.
 *
 * [hashedPhone] echoes back the submitted (protected) phone digest so the caller can map the hit
 * back onto the local address book without the server ever learning the raw number. [displayHandle]
 * is the homeserver-abstracted global handle (e.g. `@alice`), already stripped of any `:homeserver`
 * suffix by the client.
 */
data class ContactMatch(
    val hashedPhone: String,
    val userId: String,
    val displayHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
