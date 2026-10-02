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

    // GUA FORK: the Gua deployment config (resolver base URL + default account provider).
    //
    // Production values are the project's own public `gua.global` domain and are committed below.
    // Development values are read from the per-machine `local.properties` (mirroring iOS' injected
    // `Secrets` pipeline) so the non-public dev host never lands in this (public) repo. When the dev
    // keys are absent the fields stay empty and `GuaDeployment` treats the resolver as unconfigured.
    val devResolverBaseUrl = readLocalProperty("gua.resolverBaseUrl").orEmpty()
    val devDefaultAccountProvider = readLocalProperty("gua.defaultAccountProvider").orEmpty()
    val devIdentityServiceBaseUrl = readLocalProperty("gua.identityServiceBaseUrl").orEmpty()

    // `-Pgua.deployment=dev` makes RELEASE builds target the development deployment: the QA build
    // distributed through the Play internal-testing track, mirroring the iOS dev TestFlight app.
    // Without the property the release build targets production, exactly as before.
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
    // GUA FORK: `withFreshAccessToken` authenticates an identity-service call with the session's own
    // token, which means reading it from the SDK client and, as a fallback, from the persisted session.
    api(projects.libraries.matrix.api)
    implementation(projects.libraries.sessionStorage.api)
    // GUA FORK: `identityServiceMessage` words the failure that every identity-service call shares.
    implementation(projects.libraries.uiStrings)
    // GUA FORK: account genesis (ADM-008 Phase 3). Tink supplies Ed25519, which the Android keystore
    // does not generate or sign with at this minSdk; the cryptography module supplies the non-exportable
    // keystore key the Ed25519 seed is sealed under, and the preferences store holds only that sealed blob.
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
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.preferences.test)
    testImplementation(projects.libraries.sessionStorage.test)
    testImplementation(platform(libs.network.okhttp.bom))
    testImplementation(libs.network.okhttp)
    testImplementation(libs.network.mockwebserver)
}
