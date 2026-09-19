/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.features.findfriends.test"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.architecture)
    implementation(projects.tests.testutils)
    api(projects.features.findfriends.api)
}
