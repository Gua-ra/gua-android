/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import io.element.android.libraries.guaresolver.GuaDeployment

/**
 * GUA FORK: test-only [GuaDeployment] with explicit values, so login tests can exercise the
 * configured / unconfigured default-account-provider paths without the build-time selection.
 */
data class FakeGuaDeployment(
    override val resolverBaseUrl: String? = "https://resolver.gua.global",
    override val defaultAccountProvider: String? = "gua.global",
    override val identityServiceBaseUrl: String? = "https://identity.gua.global",
) : GuaDeployment
