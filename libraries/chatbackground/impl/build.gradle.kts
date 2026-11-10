import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.chatbackground.impl"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    api(projects.libraries.chatbackground.api)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.services.appnavstate.api)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.serialization.json)
    implementation(libs.vanniktech.blurhash)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.sessionStorage.test)
    testImplementation(projects.services.appnavstate.test)
    testImplementation(platform(libs.network.okhttp.bom))
    testImplementation(libs.network.mockwebserver)
}
