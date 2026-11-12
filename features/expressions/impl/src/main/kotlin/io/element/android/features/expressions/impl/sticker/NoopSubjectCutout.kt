/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import android.graphics.Bitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.sticker.SubjectCutout

/** Builds without Google Play services have no automatic cut-out. */
@ContributesBinding(AppScope::class)
class NoopSubjectCutout : SubjectCutout {
    override suspend fun cutOut(image: Bitmap): Bitmap? = null
}
