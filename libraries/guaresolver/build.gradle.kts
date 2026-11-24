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

    // Dev values come from local.properties so the non-public dev hosts stay out of this public repo.
    val devResolverBaseUrl = readLocalProperty("gua.resolverBaseUrl").orEmpty()
    val devDefaultAccountProvider = readLocalProperty("gua.defaultAccountProvider").orEmpty()
    val devIdentityServiceBaseUrl = readLocalProperty("gua.identityServiceBaseUrl").orEmpty()
    val devResolverTrustRoots = readLocalProperty("gua.resolverTrustRoots").orEmpty()

    // Points a release build at the dev deployment, for the Play internal-testing QA build.
    val useDevDeployment = (project.findProperty("gua.deployment") as? String) == "dev"

    defaultConfig {
        buildConfigFieldStr("GUA_PROD_RESOLVER_BASE_URL", "https://resolver.gua.global")
        buildConfigFieldStr("GUA_PROD_DEFAULT_ACCOUNT_PROVIDER", "gua.global")
        buildConfigFieldStr("GUA_PROD_IDENTITY_SERVICE_BASE_URL", "https://identity.gua.global")
        buildConfigFieldStr("GUA_DEV_RESOLVER_BASE_URL", devResolverBaseUrl)
        buildConfigFieldStr("GUA_DEV_DEFAULT_ACCOUNT_PROVIDER", devDefaultAccountProvider)
        buildConfigFieldStr("GUA_DEV_IDENTITY_SERVICE_BASE_URL", devIdentityServiceBaseUrl)
        // Production lists no policy key until governance keys exist.
        buildConfigFieldStr("GUA_PROD_RESOLVER_TRUST_ROOTS", "authority:gua-authority-prod:MCowBQYDK2VwAyEAmrJntRXbxGkoPe92j1epyM3RBXcx4nmr2wIzL6eb0a0=")
        buildConfigFieldStr("GUA_DEV_RESOLVER_TRUST_ROOTS", devResolverTrustRoots)
        buildConfigField("boolean", "GUA_USE_DEV_DEPLOYMENT", useDevDeployment.toString())
    }
}

setupDependencyInjection()

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.network)
    // Tink supplies Ed25519, which the keystore lacks at this minSdk; the seed is stored only sealed under a
    // non-exportable keystore key.
    implementation(projects.libraries.cryptography.api)
    implementation(projects.libraries.preferences.api)
    implementation(projects.services.toolbox.api)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.tink)
    implementation(libs.serialization.json)
    implementation(libs.timber)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(platform(libs.network.retrofit.bom))
    implementation(libs.network.retrofit)
    implementation(libs.network.retrofit.converter.serialization)

    testCommonDependencies(libs)
    testImplementation(projects.libraries.androidutils)
    testImplementation(projects.libraries.cryptography.impl)
    testImplementation(projects.libraries.cryptography.test)
    testImplementation(projects.libraries.preferences.test)
    testImplementation(libs.network.mockwebserver)
}
