/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network

import dev.zacsweers.metro.Inject
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.uri.ensureTrailingSlash
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Inject
class RetrofitFactory(
    private val okHttpClient: () -> OkHttpClient,
    private val json: () -> JsonProvider,
) {
    private val unloggedOkHttpClient by lazy {
        okHttpClient().newBuilder()
            .apply {
                interceptors().retainAll { it is UserAgentInterceptor }
                networkInterceptors().clear()
            }
            .build()
    }

    fun create(baseUrl: String): Retrofit = build(baseUrl, okHttpClient)

    /**
     * Like [create], but the calls pass through no app interceptor except [UserAgentInterceptor], so no logging or
     * debugging interceptor sees them. For exchanges whose content must never reach the log files.
     */
    fun createUnlogged(baseUrl: String): Retrofit = build(baseUrl) { unloggedOkHttpClient }

    private fun build(baseUrl: String, client: () -> OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.ensureTrailingSlash())
        .addConverterFactory(json()().asConverterFactory("application/json".toMediaType()))
        .callFactory { request -> client().newCall(request) }
        .build()
}
