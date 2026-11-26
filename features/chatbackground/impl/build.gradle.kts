import extension.setupDependencyInjection
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
    namespace = "io.element.android.features.chatbackground.impl"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    api(projects.features.chatbackground.api)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.chatbackground.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.matrixui)
    implementation(projects.libraries.mediapickers.api)

    testCommonDependencies(libs, true)
    testImplementation(projects.libraries.chatbackground.test)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.mediapickers.test)
}
