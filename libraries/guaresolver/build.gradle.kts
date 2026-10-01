import extension.buildConfigFieldStr
import extension.readLocalProperty
import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.guaresolver"

    buildFeatures {
        buildConfig = true
    }

    // Development values come from the per-machine `local.properties`, so the dev host is never committed to this public repo.
    val devResolverBaseUrl = readLocalProperty("gua.resolverBaseUrl").orEmpty()
    val devDefaultAccountProvider = readLocalProperty("gua.defaultAccountProvider").orEmpty()
    val devIdentityServiceBaseUrl = readLocalProperty("gua.identityServiceBaseUrl").orEmpty()

    val useDevDeployment = (project.findProperty("gua.deployment") as? String) == "dev"

    defaultConfig {
        buildConfigFieldStr("GUA_PROD_RESOLVER_BASE_URL", "https://resolver.gua.global")
        buildConfigFieldStr("GUA_PROD_DEFAULT_ACCOUNT_PROVIDER", "gua.global")
        buildConfigFieldStr("GUA_PROD_IDENTITY_SERVICE_BASE_URL", "https://identity.gua.global")
        buildConfigFieldStr("GUA_DEV_RESOLVER_BASE_URL", devResolverBaseUrl)
        buildConfigFieldStr("GUA_DEV_DEFAULT_ACCOUNT_PROVIDER", devDefaultAccountProvider)
        buildConfigFieldStr("GUA_DEV_IDENTITY_SERVICE_BASE_URL", devIdentityServiceBaseUrl)
        buildConfigField("boolean", "GUA_USE_DEV_DEPLOYMENT", useDevDeployment.toString())
    }
}

setupDependencyInjection()

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.network)
    // Tink supplies Ed25519, which the Android keystore does not provide at this minSdk.
    implementation(projects.libraries.cryptography.api)
    implementation(projects.libraries.preferences.api)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink)
    implementation(libs.serialization.json)
    implementation(libs.timber)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)
    implementation(libs.network.retrofit.converter.serialization)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.androidutils)
    testImplementation(projects.libraries.cryptography.impl)
    testImplementation(projects.libraries.cryptography.test)
    testImplementation(projects.libraries.preferences.test)
    testImplementation(platform(libs.network.okhttp.bom))
    testImplementation(libs.network.okhttp)
    testImplementation(libs.network.mockwebserver)
}
