/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.contentscanner.impl

import androidx.collection.LruCache
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.contentscanner.api.ContentScannerService
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.scanner.ContentScanner
import java.io.IOException

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultContentScannerService(
    private val contentScanner: ContentScanner,
) : ContentScannerService {
    private val cache = LruCache<String, Result<Boolean>>(100)

    override suspend fun scan(mediaSource: MediaSource): Result<Boolean> {
        val cachedValue = cache[mediaSource.safeUrl]
        if (cachedValue != null) return cachedValue

        return contentScanner.scan(mediaSource).also { result ->
            // Cache the result if it's successful or if the exception is not IO-related
            if (result.isSuccess || result.exceptionOrNull() !is IOException) {
                cache.put(mediaSource.safeUrl, result)
            }
        }
    }
}
