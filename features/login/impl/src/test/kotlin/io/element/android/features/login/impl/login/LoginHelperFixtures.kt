/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.ResolverClient
import io.element.android.libraries.guaresolver.genesis.AccountGenesisManager
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.oauth.test.FakeOAuthActionFlow

fun createLoginHelper(
    oAuthActionFlow: OAuthActionFlow = FakeOAuthActionFlow(),
    authenticationService: MatrixAuthenticationService = FakeMatrixAuthenticationService(),
    resolverClient: ResolverClient = FakeResolverClient(),
    // AccountGenesis defaults to off, so tests keep exercising the bare phone hint unless they turn it on.
    featureFlagService: FeatureFlagService = FakeFeatureFlagService(),
    accountGenesisManager: AccountGenesisManager = FakeAccountGenesisManager(),
    deployment: GuaDeployment = FakeGuaDeployment(),
): LoginHelper = LoginHelper(
    oAuthActionFlow = oAuthActionFlow,
    authenticationService = authenticationService,
    resolverClient = resolverClient,
    featureFlagService = featureFlagService,
    accountGenesisManager = accountGenesisManager,
    deployment = deployment,
)
