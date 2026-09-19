/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.ResolverClient

/**
 * GUA FORK: lambda-overridable fake [ResolverClient] for login presenter/flow tests.
 */
class FakeResolverClient(
    private val resolveResult: (String) -> Result<HomeserverResolution> = {
        Result.success(
            HomeserverResolution(
                exists = true,
                homeserver = ResolvedHomeserver(
                    serverName = "gua.global",
                    baseUrl = "https://matrix.gua.global",
                    masIssuer = "https://mas.gua.global",
                    region = "br",
                ),
            )
        )
    },
) : ResolverClient {
    override suspend fun resolve(e164Phone: String): Result<HomeserverResolution> = resolveResult(e164Phone)
}
