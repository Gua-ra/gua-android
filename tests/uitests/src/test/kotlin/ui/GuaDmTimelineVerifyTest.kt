/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:Suppress("DEPRECATION")

package ui

import base.PaparazziPreviewRule
import base.ScreenshotTest
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * GUA FORK Stage 4 verification: confirms that in a 1:1 direct chat the timeline suppresses
 * membership/profile/state "room change" events (and never renders a collapsed "N room changes"
 * group), while a group room still renders them. Mirrors the iOS `isDM` guards in
 * `RoomTimelineItemFactory`.
 *
 * The preview ([GuaDmTimelineSuppressionPreview], in the messages impl module) renders both
 * cases over an identical raw item set, routed through the SAME production grouper and the SAME
 * production suppression predicate the timeline factory uses. This test records that single
 * preview to its own snapshot files; it does not touch the shared golden set.
 */
@RunWith(TestParameterInjector::class)
class GuaDmTimelineVerifyTest(
    @TestParameter(valuesProvider = GuaDmTimelinePreviewProvider::class)
    val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @get:Rule(order = 0)
    val layoutLibErrorFilterStatement = LayoutLibErrorFilterStatement()

    @get:Rule(order = 1)
    val paparazziRule = PaparazziPreviewRule.createFor(preview, locale = "en")

    @Test
    fun snapshot() {
        ScreenshotTest.runTest(paparazzi = paparazziRule, preview = preview, localeStr = "en")
    }
}

object GuaDmTimelinePreviewProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context): List<ComposablePreview<AndroidPreviewInfo>> =
        AndroidComposablePreviewScanner()
            .scanPackageTrees("io.element.android.features.messages.impl.timeline")
            .getPreviews()
            .filter { it.methodName == "GuaDmTimelineSuppressionPreview" }
}
