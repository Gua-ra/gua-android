import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-compose-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.libraries.phonenumberentry"
}

setupDependencyInjection()

dependencies {
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.uiStrings)

    testCommonDependencies(libs, true)
}
