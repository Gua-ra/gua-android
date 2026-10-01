/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import java.net.URLEncoder

/** The query parameter MAS reads to bind a page to one account (MSC4198). */
const val MXID_LOGIN_HINT_PARAMETER = "org.matrix.msc4198.login_hint"

/** The rest of the URL is left byte for byte as it was: the server may compare values verbatim. */
fun String.withMxidLoginHint(userId: String): String {
    if (isBlank()) return this
    val fragmentStart = indexOf('#')
    val beforeFragment = if (fragmentStart >= 0) substring(0, fragmentStart) else this
    val fragment = if (fragmentStart >= 0) substring(fragmentStart) else ""
    val separator = when {
        !beforeFragment.contains('?') -> "?"
        beforeFragment.endsWith('?') || beforeFragment.endsWith('&') -> ""
        else -> "&"
    }
    val value = URLEncoder.encode("mxid:$userId", Charsets.UTF_8.name())
    return "$beforeFragment$separator$MXID_LOGIN_HINT_PARAMETER=$value$fragment"
}
