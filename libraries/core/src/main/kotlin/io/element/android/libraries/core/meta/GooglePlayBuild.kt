/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.core.meta

/**
 * The FLAVOR_DESCRIPTION of the gplay flavor in app/build.gradle.kts.
 */
const val GOOGLE_PLAY_FLAVOR_DESCRIPTION = "GooglePlay"

/**
 * True for the Google Play build. A feature whose manifest entries app/src/gplay/AndroidManifest.xml removes
 * stays off when this is true.
 */
val BuildMeta.isGooglePlayBuild: Boolean
    get() = flavorDescription == GOOGLE_PLAY_FLAVOR_DESCRIPTION
