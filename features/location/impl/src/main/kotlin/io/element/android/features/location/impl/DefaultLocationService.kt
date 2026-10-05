/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.location.api.BuildConfig
import io.element.android.features.location.api.LocationService
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.isGooglePlayBuild

@ContributesBinding(AppScope::class)
class DefaultLocationService(
    private val buildMeta: BuildMeta,
) : LocationService {
    override fun isServiceAvailable(): Boolean {
        // GUA FORK: location sharing is off in the Play build, which declares no location permission or service.
        return !buildMeta.isGooglePlayBuild && BuildConfig.MAPTILER_API_KEY.isNotEmpty()
    }
}
