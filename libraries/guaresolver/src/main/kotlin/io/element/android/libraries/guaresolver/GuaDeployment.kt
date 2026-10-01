/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver

interface GuaDeployment {
    val resolverBaseUrl: String?

    val defaultAccountProvider: String?

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
