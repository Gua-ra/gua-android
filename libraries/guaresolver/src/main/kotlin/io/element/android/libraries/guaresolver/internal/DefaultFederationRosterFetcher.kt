/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.internal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.guaresolver.FederationRoster
import io.element.android.libraries.guaresolver.FederationRosterFetcher
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.network.RetrofitFactory
import retrofit2.HttpException
import timber.log.Timber

/**
 * GUA FORK: default [FederationRosterFetcher]. Fetches `GET /roster` from the active
 * [GuaDeployment]'s resolver via Retrofit, reusing the app-wide [RetrofitFactory] (OkHttp +
 * kotlinx-serialization). Mirrors iOS `ResolverClient.fetchRoster`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultFederationRosterFetcher(
    private val retrofitFactory: RetrofitFactory,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) : FederationRosterFetcher {
    override suspend fun fetchRoster(): Result<FederationRoster> {
        val baseUrl = deployment.resolverBaseUrl
            ?: return Result.failure(ResolverError.NotConfigured)

        val api = try {
            retrofitFactory.create(baseUrl.ensureProtocol()).create(ResolverApi::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create resolver Retrofit instance")
            return Result.failure(ResolverError.Transport(e))
        }

        val roster = try {
            api.roster()
        } catch (e: HttpException) {
            return Result.failure(ResolverError.Server(e.code()))
        } catch (e: Exception) {
            Timber.e(e, "Roster fetch failed")
            return Result.failure(ResolverError.Transport(e))
        }

        return Result.success(roster)
    }
}
