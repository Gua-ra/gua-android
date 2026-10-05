/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.meta.GOOGLE_PLAY_FLAVOR_DESCRIPTION
import io.element.android.libraries.matrix.test.core.aBuildMeta
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSessionEnterpriseServiceTest {
    @Test
    fun `isElementCallAvailable is true outside the Google Play build`() = runTest {
        val service = DefaultSessionEnterpriseService(aBuildMeta())
        assertThat(service.isElementCallAvailable()).isTrue()
    }

    @Test
    fun `isElementCallAvailable is false in the Google Play build`() = runTest {
        val service = DefaultSessionEnterpriseService(aBuildMeta(flavorDescription = GOOGLE_PLAY_FLAVOR_DESCRIPTION))
        assertThat(service.isElementCallAvailable()).isFalse()
    }
}
