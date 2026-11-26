/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.model.event.MediaStackMember
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContentWithAttachment
import io.element.android.libraries.androidutils.system.toast
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.mediaviewer.api.MediaInfo
import io.element.android.libraries.mediaviewer.api.local.LocalMediaFactory
import io.element.android.libraries.mediaviewer.api.local.LocalMediaSaver
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.withContext
import timber.log.Timber

fun interface MediaStackSaver {
    suspend fun save(members: List<MediaStackMember>)
}

@ContributesBinding(RoomScope::class)
class DefaultMediaStackSaver(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    private val localMediaFactory: LocalMediaFactory,
    private val localMediaSaver: LocalMediaSaver,
    private val dispatchers: CoroutineDispatchers,
) : MediaStackSaver {
    override suspend fun save(members: List<MediaStackMember>) {
        val results = members.map { member -> saveOnDisk(member.media) }
        val message = if (results.all { it.isSuccess }) {
            context.resources.getQuantityString(R.plurals.gua_media_stack_saved, members.size, members.size)
        } else {
            results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { Timber.e(it, "Failed to save a media stack") }
            context.getString(CommonStrings.error_unknown)
        }
        withContext(dispatchers.main) { context.toast(message) }
    }

    private suspend fun saveOnDisk(media: TimelineItemEventContentWithAttachment): Result<Unit> {
        return matrixClient.matrixMediaLoader.downloadMediaFile(
            source = media.mediaSource,
            mimeType = media.mimeType,
            filename = media.filename,
        ).flatMap { mediaFile ->
            mediaFile.use {
                val localMedia = localMediaFactory.createFromMediaFile(mediaFile = it, mediaInfo = media.toMediaInfo())
                localMediaSaver.saveOnDisk(localMedia)
            }
        }
    }
}

private fun TimelineItemEventContentWithAttachment.toMediaInfo() = MediaInfo(
    filename = filename,
    caption = caption,
    htmlCaption = htmlCaption,
    mimeType = mimeType,
    fileSize = fileSize,
    formattedFileSize = formattedFileSize,
    fileExtension = fileExtension,
    senderId = null,
    senderName = null,
    senderAvatar = null,
    dateSent = null,
    dateSentFull = null,
    waveform = null,
    duration = null,
)
