/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.test

import android.graphics.Bitmap
import io.element.android.features.expressions.api.sticker.SubjectCutout

class FakeSubjectCutout(
    private val result: (Bitmap) -> Bitmap? = { null },
) : SubjectCutout {
    override suspend fun cutOut(image: Bitmap): Bitmap? = result(image)
}
