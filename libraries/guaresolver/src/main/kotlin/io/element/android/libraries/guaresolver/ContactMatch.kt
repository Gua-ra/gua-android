/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * A contact-discovery hit: a hashed address-book phone number that belongs to a Gua account.
 * [hashedPhone] echoes the submitted digest so the caller can map the hit back onto the local
 * address book. [displayHandle] is the global handle (e.g. `@alice`) with no `:homeserver` suffix.
 */
data class ContactMatch(
    val hashedPhone: String,
    val userId: String,
    val displayHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
