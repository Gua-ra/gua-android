/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.core.locale

import java.util.Locale

/**
 * GUA FORK: the language the app's UI is showing, as a BCP 47 tag for `ui_locales` and
 * `Accept-Language` on Gua's web pages and services.
 *
 * The app ships en, pt-BR, es and fr: every Portuguese locale shows pt-BR and any other language
 * shows English. Script and Unicode extensions are dropped, so servers can match the tag exactly
 * or by its language.
 */
object UiLanguage {
    private val SHIPPED_LANGUAGES = setOf("en", "es", "fr")

    fun tag(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "pt" -> "pt-BR"
        in SHIPPED_LANGUAGES -> Locale.Builder()
            .setLanguage(locale.language)
            .setRegion(locale.country)
            .build()
            .toLanguageTag()
        else -> "en"
    }
}

/**
 * GUA FORK: this URL with `ui_locales` set to [tag], unless it already has one. Only appends, so
 * the existing query keeps its exact encoding.
 */
fun String.withUiLocales(tag: String = UiLanguage.tag()): String {
    val fragmentStart = indexOf('#').takeIf { it >= 0 } ?: length
    val beforeFragment = substring(0, fragmentStart)
    if (UI_LOCALES_PARAM.containsMatchIn(beforeFragment)) return this
    val separator = when {
        '?' !in beforeFragment -> "?"
        beforeFragment.endsWith('?') || beforeFragment.endsWith('&') -> ""
        else -> "&"
    }
    return beforeFragment + separator + "ui_locales=" + tag + substring(fragmentStart)
}

private val UI_LOCALES_PARAM = Regex("[?&]ui_locales=")
