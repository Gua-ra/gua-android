/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.tests.konsist

import androidx.compose.runtime.Composable
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAllAnnotationsOf
import com.lemonappdev.konsist.api.ext.list.withImportNamed
import com.lemonappdev.konsist.api.ext.list.withoutName
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

class KonsistContentTest {
    @Test
    fun `assert that BuildConfig dot VersionCode is not used`() {
        Konsist
            .scopeFromProduction()
            .files
            .withImportNamed("io.element.android.x.BuildConfig")
            .assertFalse(additionalMessage = "Please do not use BuildConfig.VERSION_CODE, but use the versionCode from BuildMeta") {
                it.text.contains("BuildConfig.VERSION_CODE")
            }
    }

    // GUA FORK: English typed into a screen is never translated. The exceptions are screens
    // release builds do not show.
    @Test
    fun `production composables do not hard-code text`() {
        Konsist
            .scopeFromProduction()
            .functions()
            .withAllAnnotationsOf(Composable::class)
            .filterNot { function ->
                function.name.contains("Preview") ||
                    function.annotations.any { it.name.contains("Preview") } ||
                    (function.containingDeclaration as? KoNameProvider)?.name?.contains("Preview") == true ||
                    function.resideInPackage("..previews..")
            }
            .withoutName(
                // Developer options and debug tools.
                "AppDeveloperSettingsView",
                "DebugInfoSection",
                "DeveloperSettingsView",
                "EventDebugInfoView",
                "PushHistoryContent",
                "PushHistoryView",
                "RageshakePreferencesView",
                "ViewFolderView",
            )
            .assertFalse(additionalMessage = "Use a string resource instead of a literal in Text() or contentDescription.") { function ->
                HARDCODED_TEXT.findAll(function.text).any { match ->
                    val literal = match.groupValues[1]
                    literal !in ALLOWED_LITERALS && TEMPLATE.replace(literal, "").any(Char::isLetter)
                }
            }
    }

    // GUA FORK: an exception message is English and is not shown to users.
    @Test
    fun `error messages are not taken from exceptions`() {
        Konsist
            .scopeFromProduction()
            .files
            .assertFalse(additionalMessage = "Map the error to a string resource instead of showing its message.") {
                EXCEPTION_MESSAGE.containsMatchIn(it.text)
            }
    }

    private companion object {
        val HARDCODED_TEXT = Regex("""(?:\bText\(\s*(?:text\s*=\s*)?|contentDescription\s*=\s*)"((?:[^"\\]|\\.)*)"""")
        val TEMPLATE = Regex("""\$\{[^}]*}|\$[A-Za-z_]\w*""")
        val EXCEPTION_MESSAGE = Regex("""messageStr\s*=\s*[\w.]+\.message\b""")
        val ALLOWED_LITERALS = setOf(
            // Placeholders shown only where a WebView cannot render.
            "WebView - can't be previewed",
            // Developer mode only.
            "Clear cache for this room",
            // Metre symbols on the live location distance slider.
            "\${start}m",
            "\${end}m",
        )
    }
}
