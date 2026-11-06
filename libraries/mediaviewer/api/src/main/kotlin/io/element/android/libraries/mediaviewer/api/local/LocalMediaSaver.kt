/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.api.local

fun interface LocalMediaSaver {
    /**
     * Will save the media to the Downloads directory.
     * The [LocalMedia.uri] needs to have a file scheme.
     */
    suspend fun saveOnDisk(localMedia: LocalMedia): Result<Unit>
}
