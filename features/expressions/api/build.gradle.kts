import extension.testCommonDependencies

/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.features.expressions.api"
}

dependencies {
    api(projects.libraries.architecture)
    api(projects.libraries.matrix.api)
    implementation(projects.libraries.core)
    api(libs.serialization.json)
    api(libs.coroutines.core)
    api(libs.coil)

    testCommonDependencies(libs)
}
