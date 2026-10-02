/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * Accessor over the generated [BuildConfig] fields. Production values are committed. Development
 * values come from `local.properties` through the module's `build.gradle.kts` and are empty when absent.
 */
object GuaResolverConfig {
    const val PROD_RESOLVER_BASE_URL: String = BuildConfig.GUA_PROD_RESOLVER_BASE_URL
    const val PROD_DEFAULT_ACCOUNT_PROVIDER: String = BuildConfig.GUA_PROD_DEFAULT_ACCOUNT_PROVIDER
    const val PROD_IDENTITY_SERVICE_BASE_URL: String = BuildConfig.GUA_PROD_IDENTITY_SERVICE_BASE_URL
    const val DEV_RESOLVER_BASE_URL: String = BuildConfig.GUA_DEV_RESOLVER_BASE_URL
    const val DEV_DEFAULT_ACCOUNT_PROVIDER: String = BuildConfig.GUA_DEV_DEFAULT_ACCOUNT_PROVIDER
    const val DEV_IDENTITY_SERVICE_BASE_URL: String = BuildConfig.GUA_DEV_IDENTITY_SERVICE_BASE_URL

    /**
     * The active deployment for this build: [GuaDeployment.Development] for debug builds and for release
     * builds produced with `-Pgua.deployment=dev` (the Play internal-testing QA build),
     * [GuaDeployment.Production] otherwise.
     */
    val current: GuaDeployment
        get() = if (BuildConfig.DEBUG || BuildConfig.GUA_USE_DEV_DEPLOYMENT) GuaDeployment.Development else GuaDeployment.Production
}
