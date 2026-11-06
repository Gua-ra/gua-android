/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runUpdatingState
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload
import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.media.isPreviewEnabled
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.canChangeChatBackground
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.ui.chatbackground.collectBackgroundAsState
import io.element.android.libraries.mediapickers.api.PickerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class ChatBackgroundPresenter(
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val chatBackgroundService: ChatBackgroundService,
    private val photoProcessor: ChatBackgroundPhotoProcessor,
    private val mediaPickerProvider: PickerProvider,
) : Presenter<ChatBackgroundState> {
    @Composable
    override fun present(): ChatBackgroundState {
        val coroutineScope = rememberCoroutineScope()
        val current by chatBackgroundService.collectBackgroundAsState(room)
        val canChange by room.permissionsAsState(false) { it.canChangeChatBackground() }
        val mediaPreviewConfig = matrixClient.mediaPreviewService.mediaPreviewConfigFlow.collectAsState()
        val roomInfo = room.roomInfoFlow.collectAsState()
        val isPhotoHidden by remember {
            derivedStateOf { !mediaPreviewConfig.value.mediaPreviewValue.isPreviewEnabled(roomInfo.value.joinRule) }
        }
        var selection by remember { mutableStateOf<ChatBackground?>(null) }
        var photoDraft by remember { mutableStateOf<ChatBackgroundPhotoDraft?>(null) }
        var operation by remember { mutableStateOf(ChatBackgroundOperation.Set) }
        val action = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        val photoPicker = mediaPickerProvider.registerGalleryImagePicker { uri ->
            uri ?: return@registerGalleryImagePicker
            operation = ChatBackgroundOperation.PreparePhoto
            coroutineScope.preparePhoto(uri, action) { photoDraft = ChatBackgroundPhotoDraft(it) }
        }

        fun handleEvent(event: ChatBackgroundEvent) {
            when (event) {
                is ChatBackgroundEvent.SelectPreset -> selection = ChatBackground.Preset(event.preset)
                ChatBackgroundEvent.ChoosePhoto -> if (canChange) photoPicker.launch()
                is ChatBackgroundEvent.SetPhotoDim -> photoDraft = photoDraft?.copy(
                    dim = event.dim.coerceIn(0, ChatBackground.Photo.MAX_DIM),
                    isDarkPreview = true,
                )
                is ChatBackgroundEvent.SetPhotoAlign -> photoDraft = photoDraft?.copy(
                    alignX = event.x.coerceIn(0, ChatBackground.Photo.MAX_ALIGN),
                    alignY = event.y.coerceIn(0, ChatBackground.Photo.MAX_ALIGN),
                )
                is ChatBackgroundEvent.SetDarkPreview -> photoDraft = photoDraft?.copy(isDarkPreview = event.isDark)
                ChatBackgroundEvent.DiscardPhoto -> photoDraft = null
                ChatBackgroundEvent.Apply -> if (canChange) {
                    operation = ChatBackgroundOperation.Set
                    val draft = photoDraft
                    if (draft != null) {
                        coroutineScope.setPhoto(draft, action)
                    } else {
                        coroutineScope.set(selection ?: current, action)
                    }
                }
                ChatBackgroundEvent.Remove -> if (canChange) {
                    operation = ChatBackgroundOperation.Remove
                    coroutineScope.set(ChatBackground.None, action)
                }
                ChatBackgroundEvent.ClearError -> action.value = AsyncAction.Uninitialized
            }
        }

        return ChatBackgroundState(
            current = current,
            selection = selection ?: current,
            photoDraft = photoDraft,
            canChange = canChange,
            isPhotoHidden = isPhotoHidden,
            operation = operation,
            action = action.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.preparePhoto(uri: Uri, action: MutableState<AsyncAction<Unit>>, onPrepared: (ChatBackgroundPhotoUpload) -> Unit) = launch {
        action.value = AsyncAction.Loading
        photoProcessor.process(uri)
            .flatMap { upload ->
                val maxSize = matrixClient.getMaxFileUploadSize().getOrNull()
                if (maxSize != null && upload.data.size > maxSize) Result.failure(PhotoTooLargeException()) else Result.success(upload)
            }
            .onSuccess {
                onPrepared(it)
                action.value = AsyncAction.Uninitialized
            }
            .onFailure { action.value = AsyncAction.Failure(it) }
    }

    private fun CoroutineScope.setPhoto(draft: ChatBackgroundPhotoDraft, action: MutableState<AsyncAction<Unit>>) = launch {
        action.runUpdatingState {
            matrixClient.uploadMedia(draft.upload.mimetype, draft.upload.data).flatMap { url -> chatBackgroundService.set(room, draft.toPhoto(url)) }
        }
    }

    private fun CoroutineScope.set(background: ChatBackground, action: MutableState<AsyncAction<Unit>>) = launch {
        action.runUpdatingState { chatBackgroundService.set(room, background) }
    }
}

private fun ChatBackgroundPhotoDraft.toPhoto(url: String) = ChatBackground.Photo(
    url = url,
    mimetype = upload.mimetype,
    width = upload.width,
    height = upload.height,
    size = upload.data.size.toLong(),
    blurhash = upload.blurhash,
    dim = dim,
    alignX = alignX,
    alignY = alignY,
)
