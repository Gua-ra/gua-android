/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.mlkit

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import io.element.android.features.expressions.api.sticker.SubjectCutout
import io.element.android.features.expressions.impl.sticker.NoopSubjectCutout

@BindingContainer
@ContributesTo(AppScope::class, replaces = [NoopSubjectCutout::class])
interface MlKitSubjectCutoutBindingContainer {
    @Binds
    fun bindSubjectCutout(cutout: MlKitSubjectCutout): SubjectCutout
}
