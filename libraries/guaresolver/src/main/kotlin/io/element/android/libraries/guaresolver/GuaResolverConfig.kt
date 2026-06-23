/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: thin accessor over the generated [BuildConfig] fields, plus the build-time selection of
 * the active [GuaDeployment]. Production values are committed; development values come from
 * `local.properties` via the module's `build.gradle.kts` and are empty when absent.
 */
object GuaResolverConfig {
    const val PROD_RESOLVER_BASE_URL: String = BuildConfig.GUA_PROD_RESOLVER_BASE_URL
    const val PROD_DEFAULT_ACCOUNT_PROVIDER: String = BuildConfig.GUA_PROD_DEFAULT_ACCOUNT_PROVIDER
    const val PROD_IDENTITY_SERVICE_BASE_URL: String = BuildConfig.GUA_PROD_IDENTITY_SERVICE_BASE_URL
    const val DEV_RESOLVER_BASE_URL: String = BuildConfig.GUA_DEV_RESOLVER_BASE_URL
    const val DEV_DEFAULT_ACCOUNT_PROVIDER: String = BuildConfig.GUA_DEV_DEFAULT_ACCOUNT_PROVIDER
    const val DEV_IDENTITY_SERVICE_BASE_URL: String = BuildConfig.GUA_DEV_IDENTITY_SERVICE_BASE_URL

    /**
     * The active deployment for this build: [GuaDeployment.Development] for debug builds,
     * [GuaDeployment.Production] otherwise. Mirrors iOS `GuaDeployment.current`.
     */
    val current: GuaDeployment
        get() = if (BuildConfig.DEBUG) GuaDeployment.Development else GuaDeployment.Production
}
