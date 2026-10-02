/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalesConfigTest {
    @Test
    fun `every per-app language is a well-formed language tag`() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/locales_config.xml"))
        val locales = document.getElementsByTagName("locale")
        val tags = (0 until locales.length).map {
            locales.item(it).attributes.getNamedItemNS(ANDROID_NAMESPACE, "name").nodeValue
        }

        // "pt_BR" parses as "und", which the per-app language picker drops.
        assertThat(tags.map { Locale.forLanguageTag(it).toLanguageTag() }).isEqualTo(tags)
        assertThat(tags).containsExactly("en", "en-US", "es", "fr", "pt-BR")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
