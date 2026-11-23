/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifLibrarySetting
import io.element.android.features.expressions.api.gif.GifPage
import io.element.android.features.expressions.api.gif.GifProvider
import io.element.android.features.expressions.api.gif.GifSender
import io.element.android.features.expressions.api.gif.RecentGifsStore
import io.element.android.features.expressions.api.panel.ExpressionComposer
import io.element.android.features.expressions.api.panel.ExpressionPanelEvent
import io.element.android.features.expressions.api.panel.ExpressionPanelPresenter
import io.element.android.features.expressions.api.panel.ExpressionPanelState
import io.element.android.features.expressions.api.panel.KeyboardContent
import io.element.android.features.expressions.api.sticker.RecentStickersStore
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrarySetting
import io.element.android.features.expressions.api.sticker.StickerPack
import io.element.android.features.expressions.api.sticker.StickerPackReader
import io.element.android.features.expressions.api.sticker.StickerSender
import io.element.android.features.expressions.impl.R
import io.element.android.features.expressions.impl.editor.EditorFrame
import io.element.android.features.expressions.impl.editor.EditorGeometry
import io.element.android.features.expressions.impl.editor.StickerEditorEngine
import io.element.android.features.expressions.impl.expressionErrorMessage
import io.element.android.features.expressions.impl.keyboard.KeyboardStickers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.emoji.api.picker.EmojiPickerPresenter
import io.element.android.libraries.emoji.api.recentemojis.AddRecentEmoji
import io.element.android.libraries.emoji.api.recentemojis.GetRecentEmojis
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
class DefaultExpressionPanelPresenter(
    @Assisted private val composer: ExpressionComposer,
    private val room: JoinedRoom,
    private val featureFlagService: FeatureFlagService,
    private val stickerLibrarySetting: StickerLibrarySetting,
    private val gifLibrarySetting: GifLibrarySetting,
    private val recentStickersStore: RecentStickersStore,
    private val recentGifsStore: RecentGifsStore,
    private val stickerSender: StickerSender,
    private val gifSender: GifSender,
    private val gifProvider: GifProvider,
    private val stickerPackReader: StickerPackReader,
    private val emojiPickerPresenterFactory: EmojiPickerPresenter.Factory,
    private val getRecentEmojis: GetRecentEmojis,
    private val addRecentEmoji: AddRecentEmoji,
    private val pickerProvider: PickerProvider,
    private val editorEngine: StickerEditorEngine,
    private val keyboardStickers: KeyboardStickers,
    private val panelStore: ExpressionPanelStore,
    private val snackbarDispatcher: SnackbarDispatcher,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : ExpressionPanelPresenter {
    @AssistedFactory
    @ContributesBinding(RoomScope::class)
    interface Factory : ExpressionPanelPresenter.Factory {
        override fun create(composer: ExpressionComposer): DefaultExpressionPanelPresenter
    }

    private val emojiPickerPresenter by lazy { emojiPickerPresenterFactory.create(getRecentEmojis) }

    @Composable
    override fun present(): ExpressionPanelState {
        val coroutineScope = rememberCoroutineScope()
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.StickersAndGifs) }.collectAsState(initial = false)
        var isOpen by remember { mutableStateOf(false) }
        var hasOpened by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(ExpressionTab.Stickers) }
        var stickerQuery by remember { mutableStateOf("") }
        var gifQuery by remember { mutableStateOf("") }
        var gifBrowse by remember { mutableStateOf<GifBrowse>(GifBrowse.Trending) }
        var gifContent by remember { mutableStateOf<GifContent>(GifContent.Loading) }
        var gifReload by remember { mutableIntStateOf(0) }
        var nextGifPage by remember { mutableIntStateOf(2) }
        var packs by remember { mutableStateOf<List<StickerPack>>(emptyList()) }
        var sendingStickerUrls by remember { mutableStateOf(emptySet<String>()) }
        var overlay by remember { mutableStateOf<ExpressionOverlay?>(null) }
        var loadMoreJob by remember { mutableStateOf<Job?>(null) }

        val library by stickerLibrarySetting.library.collectAsState()
        val gifLibrary by gifLibrarySetting.library.collectAsState()
        val recentStickers by remember { recentStickersStore.recents }.collectAsState(initial = emptyList())
        val roomInfo by room.roomInfoFlow.collectAsState()
        val emojiPickerState = if (hasOpened) emojiPickerPresenter.present() else null

        LaunchedEffect(Unit) {
            panelStore.lastTab()?.let { tab = it }
        }

        suspend fun loadPacks() {
            stickerPackReader.packs(room.roomId, roomInfo.name.orEmpty(), roomInfo.avatarUrl).onSuccess { packs = it }
        }

        LaunchedEffect(hasOpened) {
            if (hasOpened) loadPacks()
        }

        val favouriteGifIds = gifLibrary.favourites.filter { it.provider == gifProvider.id }.map { it.id }
        val recentGifIds by remember { recentGifsStore.recents }.collectAsState(initial = emptyList())

        suspend fun fetchGifs(page: Int): Result<GifPage> = when {
            gifQuery.isNotBlank() -> gifProvider.search(gifQuery.trim(), page)
            gifBrowse == GifBrowse.Trending -> gifProvider.trending(page)
            else -> {
                val ids = if (gifBrowse == GifBrowse.Recent) recentGifIds else favouriteGifIds
                gifProvider.items(ids).map { GifPage(items = it, page = 1, hasNext = false) }
            }
        }

        val gifRequestKey: Any = when {
            gifQuery.isNotBlank() -> gifQuery.trim()
            gifBrowse == GifBrowse.Recent -> recentGifIds
            gifBrowse == GifBrowse.Favourites -> favouriteGifIds
            else -> gifBrowse
        }
        LaunchedEffect(isOpen, tab, gifRequestKey, gifReload) {
            if (!isOpen || tab != ExpressionTab.Gif) return@LaunchedEffect
            loadMoreJob?.cancel()
            if (!gifProvider.isAvailable) {
                gifContent = GifContent.Unavailable
                return@LaunchedEffect
            }
            gifContent = GifContent.Loading
            if (gifQuery.isNotBlank()) delay(SEARCH_DEBOUNCE)
            nextGifPage = 2
            gifContent = fetchGifs(page = 1).fold(
                onSuccess = { GifContent.Loaded(it.items.toImmutableList(), hasNext = it.hasNext, isLoadingMore = false) },
                onFailure = { GifContent.Error },
            )
        }

        fun loadMoreGifs() {
            val loaded = gifContent as? GifContent.Loaded ?: return
            if (!loaded.hasNext || loaded.isLoadingMore) return
            gifContent = loaded.copy(isLoadingMore = true)
            loadMoreJob = coroutineScope.launch {
                fetchGifs(nextGifPage)
                    .onSuccess { page ->
                        nextGifPage++
                        gifContent = loaded.copy(items = (loaded.items + page.items).toImmutableList(), hasNext = page.hasNext, isLoadingMore = false)
                    }
                    .onFailure { gifContent = loaded.copy(hasNext = false, isLoadingMore = false) }
            }
        }

        fun showError(error: Throwable, @StringRes fallback: Int = CommonStrings.error_unknown, retry: (() -> Unit)? = null) {
            val message = error.expressionErrorMessage() ?: fallback
            snackbarDispatcher.post(
                if (retry != null) {
                    SnackbarMessage(messageResId = message, actionResId = CommonStrings.action_retry, action = retry)
                } else {
                    SnackbarMessage(messageResId = message)
                }
            )
        }

        fun sendSticker(image: StickerImage) {
            if (image.url in sendingStickerUrls) return
            sendingStickerUrls = sendingStickerUrls + image.url
            sessionCoroutineScope.launch {
                stickerSender.send(image, composer.stickerRelation())
                    .onSuccess {
                        recentStickersStore.add(image)
                        composer.onSent()
                    }
                    .onFailure { showError(it, R.string.gua_sticker_send_failed) { sendSticker(image) } }
                sendingStickerUrls = sendingStickerUrls - image.url
            }
        }

        fun sendGif(item: GifItem, caption: String) {
            val preview = overlay as? ExpressionOverlay.GifPreview
            if (preview?.isSending == true) return
            if (preview != null) overlay = preview.copy(isSending = true)
            sessionCoroutineScope.launch {
                gifSender.send(item, caption, composer.inReplyToEventId, composer.timelineMode)
                    .onSuccess {
                        recentGifsStore.add(item.id)
                        composer.onSent()
                        if ((overlay as? ExpressionOverlay.GifPreview)?.item == item) overlay = null
                    }
                    .onFailure {
                        (overlay as? ExpressionOverlay.GifPreview)?.let { current -> overlay = current.copy(isSending = false) }
                        showError(it, R.string.gua_gif_send_failed) { sendGif(item, caption) }
                    }
            }
        }

        fun updateEditor(update: (StickerEditorState) -> StickerEditorState) {
            (overlay as? ExpressionOverlay.Editor)?.let { overlay = ExpressionOverlay.Editor(update(it.editor)) }
        }

        val imagePicker = pickerProvider.registerGalleryImagePicker { uri ->
            if (uri == null) return@registerGalleryImagePicker
            coroutineScope.launch {
                val showsNote = !panelStore.hasShownEditorNote()
                overlay = ExpressionOverlay.Editor(
                    StickerEditorState(picture = null, cutOut = null, isCutOutOn = false, text = "", isSaving = false, showsNote = showsNote)
                )
                val picture = editorEngine.load(uri)
                if (picture == null) {
                    overlay = null
                    snackbarDispatcher.post(SnackbarMessage(R.string.gua_stickers_editor_unreadable))
                    return@launch
                }
                updateEditor { it.copy(picture = picture) }
                val cutOut = editorEngine.cutOut(picture)
                updateEditor { it.copy(cutOut = cutOut, isCutOutOn = cutOut != null) }
            }
        }

        fun saveCreatedSticker(frame: EditorFrame, send: Boolean) {
            val editor = (overlay as? ExpressionOverlay.Editor)?.editor ?: return
            val picture = editor.shownPicture ?: return
            if (editor.isSaving) return
            updateEditor { it.copy(isSaving = true) }
            sessionCoroutineScope.launch {
                val result = runCatchingExceptions {
                    val canvas = editorEngine.render(picture, frame, editor.text)
                    val file = editorEngine.encode(canvas, editor.text) ?: error("Nothing to keep in the picture")
                    val image = stickerSender.upload(file).getOrThrow()
                    stickerLibrarySetting.add(image).getOrThrow()
                    image
                }
                result
                    .onSuccess { image ->
                        overlay = null
                        panelStore.setEditorNoteShown()
                        if (send) sendSticker(image)
                    }
                    .onFailure {
                        updateEditor { current -> current.copy(isSaving = false) }
                        showError(it)
                    }
            }
        }

        fun handlePanelEvent(event: PanelEvent) {
            when (event) {
                is PanelEvent.SelectTab -> {
                    tab = event.tab
                    coroutineScope.launch { panelStore.setLastTab(event.tab) }
                }
                is PanelEvent.UpdateQuery -> if (tab == ExpressionTab.Gif) gifQuery = event.query else stickerQuery = event.query
                is PanelEvent.InsertEmoji -> {
                    composer.insertEmoji(event.emoji)
                    coroutineScope.launch { addRecentEmoji(event.emoji) }
                }
                is PanelEvent.SendSticker -> sendSticker(event.image)
                is PanelEvent.ShowStickerActions -> overlay = ExpressionOverlay.StickerActions(event.tile, event.section)
                is PanelEvent.ToggleStickerFavourite -> {
                    if (overlay is ExpressionOverlay.StickerActions) overlay = null
                    coroutineScope.launch {
                        if (library.isFavourite(event.image.url)) {
                            stickerLibrarySetting.unfavourite(event.image.url)
                        } else {
                            stickerLibrarySetting.favourite(event.image)
                        }.onFailure { showError(it) }
                    }
                }
                is PanelEvent.RemoveRecentSticker -> {
                    overlay = null
                    coroutineScope.launch { recentStickersStore.remove(event.url) }
                }
                is PanelEvent.RequestDeleteSticker -> overlay = ExpressionOverlay.ConfirmDelete(event.shortcode)
                PanelEvent.ConfirmDeleteSticker -> {
                    val shortcode = (overlay as? ExpressionOverlay.ConfirmDelete)?.shortcode ?: return
                    overlay = null
                    coroutineScope.launch { stickerLibrarySetting.delete(shortcode).onFailure { showError(it) } }
                }
                is PanelEvent.SaveSticker -> {
                    overlay = null
                    coroutineScope.launch {
                        stickerLibrarySetting.add(event.image)
                            .onSuccess { snackbarDispatcher.post(SnackbarMessage(R.string.gua_stickers_saved)) }
                            .onFailure { showError(it) }
                    }
                }
                is PanelEvent.BrowseGifs -> {
                    gifQuery = ""
                    gifBrowse = event.browse
                }
                PanelEvent.LoadMoreGifs -> loadMoreGifs()
                PanelEvent.RetryGifs -> gifReload++
                is PanelEvent.OpenGif -> overlay = ExpressionOverlay.GifPreview(event.item, isSending = false)
                is PanelEvent.ToggleGifFavourite -> coroutineScope.launch {
                    if (event.item.id in favouriteGifIds) {
                        gifLibrarySetting.unfavourite(event.item.id)
                    } else {
                        gifLibrarySetting.favourite(event.item.id)
                    }.onFailure { showError(it) }
                }
                is PanelEvent.SendGif -> sendGif(event.item, event.caption)
                PanelEvent.CreateSticker -> imagePicker.launch()
                is PanelEvent.SetCutOut -> updateEditor { it.copy(isCutOutOn = event.isOn && it.cutOut != null) }
                is PanelEvent.UpdateStickerText -> updateEditor { it.copy(text = event.text.take(EditorGeometry.MAX_TEXT_LENGTH)) }
                is PanelEvent.SaveCreatedSticker -> saveCreatedSticker(event.frame, event.send)
                PanelEvent.DismissOverlay -> {
                    if (overlay is ExpressionOverlay.Editor) coroutineScope.launch { panelStore.setEditorNoteShown() }
                    overlay = null
                }
            }
        }

        fun receiveKeyboardContent(content: KeyboardContent, fallback: () -> Unit) {
            val received = keyboardStickers.take(content) ?: return fallback()
            sessionCoroutineScope.launch {
                when (val outcome = keyboardStickers.send(received, composer)) {
                    KeyboardStickers.Outcome.NotOurs -> fallback()
                    is KeyboardStickers.Outcome.SentSticker -> recentStickersStore.add(outcome.image)
                    KeyboardStickers.Outcome.SentGif -> Unit
                    is KeyboardStickers.Outcome.Failed -> showError(outcome.error)
                }
            }
        }

        fun handleEvent(event: ExpressionPanelEvent) {
            if (!isEnabled) {
                if (event is ExpressionPanelEvent.ReceiveKeyboardContent) event.fallback()
                return
            }
            when (event) {
                ExpressionPanelEvent.Open -> {
                    isOpen = true
                    hasOpened = true
                }
                ExpressionPanelEvent.Close -> isOpen = false
                is ExpressionPanelEvent.ShowTimelineSticker -> {
                    overlay = ExpressionOverlay.TimelineSticker(event.image, packName = packs.packNameOf(event.image.url))
                    if (!hasOpened) {
                        coroutineScope.launch {
                            loadPacks()
                            (overlay as? ExpressionOverlay.TimelineSticker)?.let { current ->
                                overlay = current.copy(packName = packs.packNameOf(current.image.url))
                            }
                        }
                    }
                }
                is ExpressionPanelEvent.ReceiveKeyboardContent -> receiveKeyboardContent(event.content, event.fallback)
            }
        }

        val sections = remember(recentStickers, library, packs, stickerQuery) {
            StickerSections.build(recentStickers, library, packs, stickerQuery)
        }

        return DefaultExpressionPanelState(
            isEnabled = isEnabled,
            isOpen = isEnabled && isOpen,
            tab = tab,
            stickerQuery = stickerQuery,
            gifQuery = gifQuery,
            emojiPickerState = emojiPickerState,
            stickerSections = if (isEnabled) sections else persistentListOf(),
            sendingStickerUrls = sendingStickerUrls.toImmutableSet(),
            favouriteStickerUrls = library.favourites.map { it.url }.toImmutableSet(),
            savedStickerUrls = library.stickers.map { it.image.url }.toImmutableSet(),
            gifBrowse = gifBrowse,
            gifContent = gifContent,
            favouriteGifIds = favouriteGifIds.toImmutableSet(),
            overlay = overlay,
            panelSink = ::handlePanelEvent,
            eventSink = ::handleEvent,
        )
    }

    private fun List<StickerPack>.packNameOf(url: String): String? = firstOrNull { pack -> pack.stickers.any { it.image.url == url } }?.displayName

    private companion object {
        val SEARCH_DEBOUNCE = 300.milliseconds
    }
}
