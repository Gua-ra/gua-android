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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.api.ChatBackgroundPhotoUpload
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.mediapickers.api.PickerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AssistedInject
class ChatThemePresenter(
    @Assisted private val target: ChatThemeTarget,
    private val matrixClient: MatrixClient,
    private val photoProcessor: ChatBackgroundPhotoProcessor,
    private val mediaPickerProvider: PickerProvider,
) : Presenter<ChatThemeState> {
    @AssistedFactory
    fun interface Factory {
        fun create(target: ChatThemeTarget): ChatThemePresenter
    }

    @Composable
    override fun present(): ChatThemeState {
        val coroutineScope = rememberCoroutineScope()
        val targetState = target.present()
        val current = targetState.current
        val canChange = targetState.hasPermission == true
        var page by remember { mutableStateOf<ChatThemePage>(ChatThemePage.Gallery) }
        var preview by remember { mutableStateOf<ChatThemePreview?>(null) }
        var photoDraft by remember { mutableStateOf<ChatBackgroundPhotoDraft?>(null) }
        var operation by remember { mutableStateOf(ChatThemeOperation.Save) }
        val action = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        val photoPicker = mediaPickerProvider.registerGalleryImagePicker { uri ->
            uri ?: return@registerGalleryImagePicker
            operation = ChatThemeOperation.PreparePhoto
            coroutineScope.preparePhoto(uri, action) { photoDraft = ChatBackgroundPhotoDraft(it) }
        }

        fun onSaved() {
            photoDraft = null
            preview = null
            page = ChatThemePage.Gallery
        }

        fun save() {
            val draft = photoDraft
            val previewed = preview
            val picker = page as? ChatThemePage.BubblePicker
            val write: suspend () -> Result<Unit> = when {
                draft != null -> {
                    {
                        matrixClient.uploadMedia(draft.upload.mimetype, draft.upload.data)
                            .flatMap { url -> target.set(current.copy(background = draft.toPhoto(url))) }
                    }
                }
                previewed != null -> {
                    { target.set(previewed.saved) }
                }
                picker != null -> {
                    { target.set(current.copy(bubble = picker.selection)) }
                }
                else -> return
            }
            operation = ChatThemeOperation.Save
            coroutineScope.save(action, ::onSaved, write)
        }

        fun handleEvent(event: ChatThemeEvent) {
            when (event) {
                is ChatThemeEvent.SelectTheme -> if (canChange) preview = ChatThemePreview(shown = event.theme, saved = event.theme)
                ChatThemeEvent.SelectDefault -> if (canChange) {
                    val shown = if (target.mode == ChatThemeMode.Chat) targetState.defaultTheme else ChatTheme.STOCK
                    preview = ChatThemePreview(shown = shown, saved = ChatTheme.STOCK)
                }
                ChatThemeEvent.OpenBubblePicker -> if (canChange) page = ChatThemePage.BubblePicker(current.bubble)
                is ChatThemeEvent.SelectBubble -> (page as? ChatThemePage.BubblePicker)?.let { page = it.copy(selection = event.bubble) }
                ChatThemeEvent.OpenWallpaperPicker -> if (canChange) page = ChatThemePage.WallpaperPicker
                is ChatThemeEvent.SelectWallpaper -> if (canChange) {
                    val theme = current.copy(background = event.background)
                    preview = ChatThemePreview(shown = theme, saved = theme)
                }
                ChatThemeEvent.ChoosePhoto -> if (canChange) photoPicker.launch()
                is ChatThemeEvent.SetPhotoDim -> photoDraft = photoDraft?.copy(
                    dim = event.dim.coerceIn(0, ChatBackground.Photo.MAX_DIM),
                    isDarkPreview = true,
                )
                is ChatThemeEvent.SetPhotoAlign -> photoDraft = photoDraft?.copy(
                    alignX = event.x.coerceIn(0, ChatBackground.Photo.MAX_ALIGN),
                    alignY = event.y.coerceIn(0, ChatBackground.Photo.MAX_ALIGN),
                )
                is ChatThemeEvent.SetDarkPreview -> {
                    val draft = photoDraft
                    if (draft != null) {
                        photoDraft = draft.copy(isDarkPreview = event.isDark)
                    } else {
                        preview = preview?.copy(isDarkPreview = event.isDark)
                    }
                }
                ChatThemeEvent.Save -> if (canChange) save()
                ChatThemeEvent.Cancel -> when {
                    photoDraft != null -> photoDraft = null
                    preview != null -> preview = null
                    else -> page = ChatThemePage.Gallery
                }
                ChatThemeEvent.ClearError -> action.value = AsyncAction.Uninitialized
            }
        }

        return ChatThemeState(
            mode = target.mode,
            current = current,
            defaultTheme = targetState.defaultTheme,
            hasPermission = targetState.hasPermission,
            isPhotoHidden = targetState.isPhotoHidden,
            page = page,
            preview = preview,
            photoDraft = photoDraft,
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

    private fun CoroutineScope.save(action: MutableState<AsyncAction<Unit>>, onSaved: () -> Unit, write: suspend () -> Result<Unit>) = launch {
        action.value = AsyncAction.Loading
        write()
            .onSuccess {
                onSaved()
                action.value = AsyncAction.Uninitialized
            }
            .onFailure { action.value = AsyncAction.Failure(it) }
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
