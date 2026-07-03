/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * GUA FORK: the Gua backend deployment a build talks to. Bundles the environment-specific service
 * endpoints (currently the federation resolver) plus the default account provider, so the rest of
 * the app never hardcodes a host. Android counterpart of iOS `GuaDeployment`.
 *
 * The active deployment is chosen at build time, so the same source ships to every environment:
 * - **Release** builds use [Production].
 * - **Debug** builds use [Development].
 *
 * Production endpoints are the project's own `gua.global` domain and are safe to commit (see
 * [GuaResolverConfig.PROD_RESOLVER_BASE_URL]). Development endpoints are injected per-machine via
 * `local.properties` (`gua.resolverBaseUrl`, `gua.defaultAccountProvider`) and surfaced through
 * `BuildConfig`, so the non-public dev host is never committed to this (public) repo — same as iOS'
 * `Secrets` pipeline.
 */
interface GuaDeployment {
    /** Federation resolver base URL, or `null` when the deployment is unconfigured. */
    val resolverBaseUrl: String?

    /** Default account provider (homeserver host) for the deployment, or `null` when unconfigured. */
    val defaultAccountProvider: String?

    /**
     * Identity-service base URL (phone/OTP IdP + contact discovery), or `null` when unconfigured.
     * Android counterpart of iOS `GuaDeployment.identityServiceBaseURL`.
     */
    val identityServiceBaseUrl: String?

    data object Production : GuaDeployment {
        override val resolverBaseUrl: String = GuaResolverConfig.PROD_RESOLVER_BASE_URL
        override val defaultAccountProvider: String = GuaResolverConfig.PROD_DEFAULT_ACCOUNT_PROVIDER
        override val identityServiceBaseUrl: String = GuaResolverConfig.PROD_IDENTITY_SERVICE_BASE_URL
    }

    data object Development : GuaDeployment {
        override val resolverBaseUrl: String? = GuaResolverConfig.DEV_RESOLVER_BASE_URL.ifEmpty { null }
        override val defaultAccountProvider: String? = GuaResolverConfig.DEV_DEFAULT_ACCOUNT_PROVIDER.ifEmpty { null }
        override val identityServiceBaseUrl: String? = GuaResolverConfig.DEV_IDENTITY_SERVICE_BASE_URL.ifEmpty { null }
    }
}
