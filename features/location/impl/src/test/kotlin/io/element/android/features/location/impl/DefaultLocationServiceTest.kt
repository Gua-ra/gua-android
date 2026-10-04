/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.api.BuildConfig
import io.element.android.libraries.core.meta.GOOGLE_PLAY_FLAVOR_DESCRIPTION
import io.element.android.libraries.matrix.test.core.aBuildMeta
import org.junit.Test

class DefaultLocationServiceTest {
    @Test
    fun `isServiceAvailable should return value depending on BuildConfig MAPTILER_API_KEY`() {
        val locationService = DefaultLocationService(aBuildMeta())
        assertThat(locationService.isServiceAvailable()).isEqualTo(
            BuildConfig.MAPTILER_API_KEY.isNotEmpty()
        )
    }

    @Test
    fun `isServiceAvailable is false in the Google Play build`() {
        val locationService = DefaultLocationService(aBuildMeta(flavorDescription = GOOGLE_PLAY_FLAVOR_DESCRIPTION))
        assertThat(locationService.isServiceAvailable()).isFalse()
    }
}
