/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import java.net.URLEncoder

/** GUA FORK: the query parameter MAS reads to bind a page to one account (MSC4198). */
const val MXID_LOGIN_HINT_PARAMETER = "org.matrix.msc4198.login_hint"

/**
 * GUA FORK: appends `org.matrix.msc4198.login_hint=mxid:<userId>` to this URL, so a page opened for
 * the signed-in account refuses to continue under a browser session that belongs to someone else.
 *
 * The rest of the URL is left byte for byte as it was: the existing query keeps its own
 * percent-encoding, and a fragment stays at the end. Re-serialising the query would decode and
 * re-encode values the server may compare verbatim. A blank URL is returned unchanged.
 */
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
