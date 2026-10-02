/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

/**
 * The Gua backend deployment a build talks to: the service endpoints plus the default account
 * provider, so the rest of the app never hardcodes a host.
 *
 * The deployment is chosen at build time, see [GuaResolverConfig.current]. Production endpoints are
 * the public `gua.global` domain and are committed. Development endpoints are injected per machine
 * through `local.properties` and `BuildConfig`, so the dev host is never committed to this public repo.
 */
interface GuaDeployment {
    /** Federation resolver base URL, or `null` when the deployment is unconfigured. */
    val resolverBaseUrl: String?

    /** Default account provider (homeserver host) for the deployment, or `null` when unconfigured. */
    val defaultAccountProvider: String?

    /** Identity-service base URL (phone/OTP IdP + contact discovery), or `null` when unconfigured. */
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
