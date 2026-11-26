/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import androidx.annotation.StringRes

/** Must match the credit that bake.mjs writes to manifest.json. */
data class ChatPhotoCredit(
    val author: String,
    val licence: ChatPhotoLicence,
    val sourceUrl: String?,
)

/** [manifestName] is the licence as manifest.json names it, and shows untranslated unless the kind has a [nameRes]. */
enum class ChatPhotoLicence(val manifestName: String, val deedUrl: String, @StringRes val nameRes: Int? = null) {
    Cc0(manifestName = "CC0 1.0", deedUrl = "https://creativecommons.org/publicdomain/zero/1.0/"),
    CcBy4(manifestName = "CC BY 4.0", deedUrl = "https://creativecommons.org/licenses/by/4.0/"),
    AllRightsReserved(
        manifestName = "All rights reserved",
        deedUrl = "https://github.com/Gua-ra/gua-android/blob/develop/tools/wallpapers/licenses/LicenseRef-Gabriel-Bonfa-Photo.txt",
        nameRes = R.string.gua_chat_theme_photo_licence_all_rights_reserved,
    ),
}
