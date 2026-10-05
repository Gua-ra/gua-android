/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.core.meta

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GooglePlayBuildTest {
    @Test
    fun `the gplay flavor is the Google Play build`() {
        assertThat(aBuildMeta(flavorDescription = GOOGLE_PLAY_FLAVOR_DESCRIPTION).isGooglePlayBuild).isTrue()
    }

    @Test
    fun `other flavors are not the Google Play build`() {
        assertThat(aBuildMeta(flavorDescription = "FDroid").isGooglePlayBuild).isFalse()
        assertThat(aBuildMeta(flavorDescription = "").isGooglePlayBuild).isFalse()
    }

    private fun aBuildMeta(flavorDescription: String) = BuildMeta(
        buildType = BuildType.RELEASE,
        isDebuggable = false,
        applicationName = "",
        productionApplicationName = "",
        desktopApplicationName = "",
        applicationId = "",
        isEnterpriseBuild = false,
        lowPrivacyLoggingEnabled = false,
        versionName = "",
        versionCode = 0,
        gitRevision = "",
        gitBranchName = "",
        flavorDescription = flavorDescription,
        flavorShortDescription = "",
    )
}
