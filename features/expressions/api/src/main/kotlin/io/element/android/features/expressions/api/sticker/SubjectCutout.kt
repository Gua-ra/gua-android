/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.api.sticker

import android.graphics.Bitmap

fun interface SubjectCutout {
    /** The subjects of [image] on a transparent background, or null when none could be cut out. */
    suspend fun cutOut(image: Bitmap): Bitmap?
}
