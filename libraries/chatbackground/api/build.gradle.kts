import extension.testCommonDependencies

/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.chatbackground.api"
}

dependencies {
    implementation(projects.libraries.core)
    api(projects.libraries.matrix.api)
    implementation(libs.serialization.json)
    implementation(libs.androidx.annotationjvm)
    api(libs.coroutines.core)

    testCommonDependencies(libs)
}
