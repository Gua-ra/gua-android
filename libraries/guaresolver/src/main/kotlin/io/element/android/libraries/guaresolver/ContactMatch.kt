/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: a contact-discovery hit, an address-book phone number that belongs to a Gua account.
 * Android counterpart of iOS `ContactMatch`.
 *
 * [phoneNumber] is the submitted E.164 number that matched, so the caller can map the hit back onto
 * the local address book. [displayHandle] is the homeserver-abstracted global handle (e.g. `@alice`),
 * already stripped of any `:homeserver` suffix by the client.
 */
data class ContactMatch(
    val phoneNumber: String,
    val userId: String,
    val displayHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
)
