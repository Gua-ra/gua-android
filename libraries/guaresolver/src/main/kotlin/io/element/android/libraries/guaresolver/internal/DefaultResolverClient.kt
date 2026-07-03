/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.ResolverClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import retrofit2.HttpException
import timber.log.Timber

/**
 * GUA FORK: default [ResolverClient]. Talks to the active [GuaDeployment]'s resolver via Retrofit,
 * reusing the app-wide [RetrofitFactory] (OkHttp + kotlinx-serialization). Mirrors iOS
 * `ResolverClient`.
 *
 * PII note: the phone number is sent plaintext over TLS to the resolver only, and is never logged.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultResolverClient(
    private val retrofitFactory: RetrofitFactory,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : ResolverClient {
    override suspend fun resolve(e164Phone: String): Result<HomeserverResolution> {
        val baseUrl = deployment.resolverBaseUrl
            ?: return Result.failure(ResolverError.NotConfigured)

        val api = try {
            retrofitFactory.create(baseUrl.ensureProtocol()).create(ResolverApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create resolver Retrofit instance")
            return Result.failure(ResolverError.Transport(e))
        }

        val response = try {
            api.resolve(ResolveRequest(phone = e164Phone))
        } catch (e: HttpException) {
            return Result.failure(ResolverError.Server(e.code()))
        } catch (e: Exception) {
            Timber.e(e, "Resolver lookup failed")
            return Result.failure(ResolverError.Transport(e))
        }

        val ref = (if (response.exists) response.homeserver else response.registerAt)
            ?: return Result.failure(ResolverError.MalformedResponse)

        return Result.success(
            HomeserverResolution(
                exists = response.exists,
                homeserver = ResolvedHomeserver(
                    serverName = ref.serverName,
                    baseUrl = ref.baseUrl,
                    masIssuer = ref.masIssuer,
                    region = ref.region,
                ),
            )
        )
    }
}
