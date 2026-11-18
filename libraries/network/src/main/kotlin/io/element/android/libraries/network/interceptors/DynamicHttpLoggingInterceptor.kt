/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.network.interceptors

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import retrofit2.http.Path
import retrofit2.http.Url
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val REDACTED = "<redacted>"

/**
 * Header names whose values may be written to the log. Every other header value is written as [REDACTED].
 */
private val visibleHeaders = setOf(
    "accept",
    "accept-encoding",
    "accept-language",
    "cache-control",
    "connection",
    "content-encoding",
    "content-length",
    "content-type",
    "date",
    "etag",
    "expires",
    "pragma",
    "retry-after",
    "server",
    "transfer-encoding",
    "user-agent",
    "vary",
)

/**
 * Logs HTTP exchanges at the DEBUG log level and above as method, origin, path, status, timing and body sizes.
 * Bodies, query strings and header values outside [visibleHeaders] are never written: the log files are kept for days
 * on the device and attached to bug reports. A path is written only when a Retrofit service method declares it in
 * full, since any other path can carry user data such as the coordinates in a static map URL.
 */
@Inject
@SingleIn(AppScope::class)
class DynamicHttpLoggingInterceptor(
    private val appPreferencesStore: AppPreferencesStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // This is called in a separate thread, so calling `runBlocking` here should be fine, it should be also instant after the value is cached
        val logLevel = runBlocking { appPreferencesStore.getTracingLogLevelFlow().first() }
        val request = chain.request()
        if (logLevel < LogLevel.DEBUG) return chain.proceed(request)
        val showPath = request.hasDeclaredPath()
        val url = request.url.loggable(showPath)
        Timber.d(describeRequest(request, url))
        val startNs = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Timber.d("<-- HTTP FAILED ${e.javaClass.simpleName}: ${e.message} $url (${elapsedMs(startNs)}ms)")
            throw e
        }
        Timber.d(describeResponse(response, request.url, showPath, elapsedMs(startNs)))
        return response
    }

    private fun describeRequest(request: Request, url: String): String = buildString {
        append("--> ").append(request.method).append(' ').append(url)
        val body = request.body
        if (body != null) {
            append(" (").append(describeSize(body.contentLength())).append(" body)")
            if (request.header("Content-Type") == null) {
                body.contentType()?.let { append("\nContent-Type: ").append(it) }
            }
        }
        appendHeaders(request.headers)
    }

    private fun describeResponse(response: Response, requestUrl: HttpUrl, showPath: Boolean, tookMs: Long): String = buildString {
        val finalUrl = response.request.url
        // A redirect target is chosen by the server, so its path is shown only when it is the declared one.
        val showFinalPath = showPath && finalUrl.encodedPath == requestUrl.encodedPath
        append("<-- ").append(response.code).append(' ').append(finalUrl.loggable(showFinalPath))
        val priorCodes = generateSequence(response.priorResponse) { it.priorResponse }.map { it.code }.toList()
        if (priorCodes.isNotEmpty()) append(" (after ").append(priorCodes.asReversed().joinToString()).append(')')
        append(" (").append(tookMs).append("ms, ").append(describeSize(response.body.contentLength())).append(" body)")
        appendHeaders(response.headers)
    }

    private fun StringBuilder.appendHeaders(headers: Headers) {
        for ((name, value) in headers) {
            append('\n').append(name).append(": ")
            append(if (name.lowercase() in visibleHeaders) value else REDACTED)
        }
    }

    private fun describeSize(contentLength: Long): String =
        if (contentLength < 0) "unknown-length" else "$contentLength-byte"

    private fun elapsedMs(startNs: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

    private fun HttpUrl.loggable(showPath: Boolean): String = buildString {
        append(scheme).append("://").append(host)
        if (port != HttpUrl.defaultPort(scheme)) append(':').append(port)
        append(if (showPath) encodedPath else "/$REDACTED")
    }

    private fun Request.hasDeclaredPath(): Boolean {
        val method = tag(Invocation::class.java)?.method() ?: return false
        return method.parameterAnnotations.none { annotations -> annotations.any { it is Path || it is Url } }
    }
}
