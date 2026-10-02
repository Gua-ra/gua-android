/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.core.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class UiLanguageTest {
    @Test
    fun `every Portuguese locale is Brazilian Portuguese`() {
        assertThat(tagOf("pt-BR")).isEqualTo("pt-BR")
        assertThat(tagOf("pt-PT")).isEqualTo("pt-BR")
        assertThat(tagOf("pt")).isEqualTo("pt-BR")
    }

    @Test
    fun `shipped languages keep their region`() {
        assertThat(tagOf("en-US")).isEqualTo("en-US")
        assertThat(tagOf("es-MX")).isEqualTo("es-MX")
        assertThat(tagOf("es-419")).isEqualTo("es-419")
        assertThat(tagOf("fr-CA")).isEqualTo("fr-CA")
        assertThat(tagOf("fr")).isEqualTo("fr")
    }

    @Test
    fun `a language Gua does not ship is English`() {
        assertThat(tagOf("de-DE")).isEqualTo("en")
        assertThat(tagOf("ja")).isEqualTo("en")
    }

    @Test
    fun `script and Unicode extensions are dropped`() {
        assertThat(tagOf("pt-BR-u-mu-celsius")).isEqualTo("pt-BR")
        assertThat(tagOf("fr-CA-u-fw-mon")).isEqualTo("fr-CA")
        assertThat(tagOf("en-Latn-GB-u-ms-ussystem")).isEqualTo("en-GB")
    }

    @Test
    fun `ui_locales is appended to a URL without a query`() {
        assertThat("https://auth.gua.global/account/".withUiLocales("pt-BR"))
            .isEqualTo("https://auth.gua.global/account/?ui_locales=pt-BR")
    }

    @Test
    fun `ui_locales is appended without re-encoding the query`() {
        assertThat("https://auth.gua.global/authorize?login_hint=%2B5511999&state=a%20b".withUiLocales("es"))
            .isEqualTo("https://auth.gua.global/authorize?login_hint=%2B5511999&state=a%20b&ui_locales=es")
    }

    @Test
    fun `ui_locales goes before a fragment`() {
        assertThat("https://idp.gua.global/login/enroll/abc?x=1#top".withUiLocales("fr"))
            .isEqualTo("https://idp.gua.global/login/enroll/abc?x=1&ui_locales=fr#top")
        assertThat("https://idp.gua.global/signin?".withUiLocales("fr"))
            .isEqualTo("https://idp.gua.global/signin?ui_locales=fr")
    }

    @Test
    fun `a URL that already names ui_locales is left alone`() {
        val url = "https://auth.gua.global/account/?ui_locales=en"
        assertThat(url.withUiLocales("pt-BR")).isEqualTo(url)
    }

    private fun tagOf(languageTag: String) = UiLanguage.tag(Locale.forLanguageTag(languageTag))
}
