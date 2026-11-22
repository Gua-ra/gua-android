/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

/** Must match the credit that bake.mjs writes to manifest.json. */
data class ChatPhotoCredit(
    val author: String,
    val licence: ChatPhotoLicence,
    val sourceUrl: String?,
)

enum class ChatPhotoLicence(val displayName: String, val deedUrl: String) {
    Cc0(displayName = "CC0 1.0", deedUrl = "https://creativecommons.org/publicdomain/zero/1.0/"),
    CcBy4(displayName = "CC BY 4.0", deedUrl = "https://creativecommons.org/licenses/by/4.0/"),
}
