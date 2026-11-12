import extension.buildConfigFieldStr
import extension.environmentOrGradleValue
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
    namespace = "io.element.android.features.expressions.impl"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigFieldStr("KLIPY_API_KEY", environmentOrGradleValue("KLIPY_API_KEY", "gua.klipyApiKey", defaultValue = ""))
        buildConfigFieldStr("GIF_PROXY_HOST", environmentOrGradleValue("GIF_PROXY_HOST", "gua.gifProxyHost", defaultValue = ""))
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

setupDependencyInjection()

dependencies {
    api(projects.features.expressions.api)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.architecture)
    implementation(projects.libraries.designsystem)
    implementation(projects.libraries.emoji.api)
    implementation(projects.libraries.featureflag.api)
    implementation(projects.libraries.matrixmedia.api)
    implementation(projects.libraries.mediapickers.api)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.mediaupload.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.sessionStorage.api)
    implementation(projects.libraries.uiStrings)
    implementation(projects.services.toolbox.api)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)
    implementation(libs.serialization.json)
    implementation(libs.vanniktech.blurhash)

    testCommonDependencies(libs)
    testImplementation(projects.features.expressions.test)
    testImplementation(projects.libraries.emoji.test)
    testImplementation(projects.libraries.featureflag.test)
    testImplementation(projects.libraries.mediapickers.test)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.mediaupload.test)
    testImplementation(projects.libraries.sessionStorage.test)
    testImplementation(projects.services.toolbox.test)
    testImplementation(libs.network.mockwebserver)
}
