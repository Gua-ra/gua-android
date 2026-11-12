/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.panel

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.gif.GifFile
import io.element.android.features.expressions.api.gif.GifItem
import io.element.android.features.expressions.api.gif.GifPage
import io.element.android.features.expressions.api.gif.GifRendition
import io.element.android.features.expressions.api.gif.GifSize
import io.element.android.features.expressions.api.panel.ExpressionComposer
import io.element.android.features.expressions.api.panel.ExpressionPanelEvent
import io.element.android.features.expressions.api.panel.ExpressionPanelState
import io.element.android.features.expressions.api.panel.KeyboardContent
import io.element.android.features.expressions.api.sticker.OwnSticker
import io.element.android.features.expressions.api.sticker.PackSticker
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerLibrary
import io.element.android.features.expressions.api.sticker.StickerPack
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.impl.R
import io.element.android.features.expressions.impl.keyboard.FakeImageFiles
import io.element.android.features.expressions.impl.keyboard.KeyboardStickers
import io.element.android.features.expressions.impl.sticker.StickerImageEncoder
import io.element.android.features.expressions.test.FakeGifLibrarySetting
import io.element.android.features.expressions.test.FakeGifProvider
import io.element.android.features.expressions.test.FakeGifSender
import io.element.android.features.expressions.test.FakeRecentGifsStore
import io.element.android.features.expressions.test.FakeRecentStickersStore
import io.element.android.features.expressions.test.FakeStickerLibrarySetting
import io.element.android.features.expressions.test.FakeStickerPackReader
import io.element.android.features.expressions.test.FakeStickerSender
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.emoji.test.fakeEmojiPickerPresenterFactory
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultExpressionPanelPresenterTest {
    private val cat = StickerImage(url = "mxc://gua.global/cat", body = "Cat", info = null)
    private val dog = StickerImage(url = "mxc://gua.global/dog", body = "Dog", info = null)
    private val wave = StickerImage(url = "mxc://gua.global/wave", body = "Waving café", info = null)
    private val pack = StickerPack(
        eventType = "m.room.image_pack",
        stateKey = "cats",
        displayName = "Cats",
        avatarUrl = null,
        stickers = listOf(PackSticker("wave", wave)),
    )

    @Test
    fun `with the flag off the panel never opens and keyboard content keeps its old path`() = runTest {
        var fallbacks = 0
        createPresenter(isEnabled = false).test {
            val state = awaitItem()
            state.eventSink(ExpressionPanelEvent.Open)
            state.eventSink(ExpressionPanelEvent.ReceiveKeyboardContent(aKeyboardContent("image/gif")) { fallbacks++ })
            val last = consumeItemsUntilTimeout().lastOrNull() ?: state
            assertThat(last.isEnabled).isFalse()
            assertThat(last.isOpen).isFalse()
            assertThat(fallbacks).isEqualTo(1)
        }
    }

    @Test
    fun `the first open shows Stickers, and the tab chosen is remembered`() = runTest {
        val store = InMemoryExpressionPanelStore()
        createPresenter(panelStore = store).test {
            val state = awaitEnabled()
            state.eventSink(ExpressionPanelEvent.Open)
            val open = consumeItemsUntilPredicate { it.isOpen }.last().asDefault()
            assertThat(open.tab).isEqualTo(ExpressionTab.Stickers)

            open.panelSink(PanelEvent.SelectTab(ExpressionTab.Emoji))
            assertThat(consumeItemsUntilPredicate { it.asDefault().tab == ExpressionTab.Emoji }.last().asDefault().tab).isEqualTo(ExpressionTab.Emoji)
            assertThat(store.tab).isEqualTo(ExpressionTab.Emoji)
        }
    }

    @Test
    fun `the last tab is restored`() = runTest {
        createPresenter(panelStore = InMemoryExpressionPanelStore(tab = ExpressionTab.Emoji)).test {
            assertThat(awaitEnabled().tab).isEqualTo(ExpressionTab.Emoji)
        }
    }

    @Test
    fun `sections come in order, and search matches bodies and shortcodes across every source`() = runTest {
        createPresenter(
            recents = listOf(cat),
            library = StickerLibrary(favourites = emptyList(), stickers = listOf(OwnSticker("sdog", dog, 1))),
            packs = listOf(pack),
        ).test {
            awaitEnabled().eventSink(ExpressionPanelEvent.Open)
            val open = consumeItemsUntilPredicate { state -> state.asDefault().stickerSections.size == 3 }.last().asDefault()
            assertThat(open.stickerSections.map { it.kind }).containsExactly(
                StickerSectionKind.Recent,
                StickerSectionKind.Mine,
                StickerSectionKind.Pack("m.room.image_pack", "cats", "Cats", null),
            ).inOrder()

            open.panelSink(PanelEvent.UpdateQuery("CAFE"))
            val searched = consumeItemsUntilPredicate { it.asDefault().stickerQuery == "CAFE" }.last().asDefault()
            assertThat(searched.stickerSections.map { it.kind to it.tiles.size }).containsExactly(
                StickerSectionKind.Recent to 0,
                StickerSectionKind.Pack("m.room.image_pack", "cats", "Cats", null) to 1,
            ).inOrder()

            searched.panelSink(PanelEvent.UpdateQuery("sdog"))
            val byShortcode = consumeItemsUntilPredicate { it.asDefault().stickerQuery == "sdog" }.last().asDefault()
            assertThat(byShortcode.stickerSections.map { it.kind }).contains(StickerSectionKind.Mine)
        }
    }

    @Test
    fun `a sticker is sent with the reply, then goes to recents and ends the reply`() = runTest {
        val composer = RecordingComposer(inReplyToEventId = AN_EVENT_ID)
        val recents = FakeRecentStickersStore()
        val relations = mutableListOf<StickerRelation>()
        createPresenter(
            composer = composer,
            recentStickersStore = recents,
            stickerSender = FakeStickerSender(sendResult = { _, relation ->
                relations += relation
                Result.success(AN_EVENT_ID)
            }),
        ).test {
            awaitEnabled().panelSink(PanelEvent.SendSticker(cat))
            consumeItemsUntilPredicate { cat.url in it.asDefault().sendingStickerUrls }
            consumeItemsUntilPredicate { it.asDefault().sendingStickerUrls.isEmpty() }
            assertThat(relations).containsExactly(StickerRelation.Reply(AN_EVENT_ID))
            assertThat(recents.recents.value).containsExactly(cat)
            assertThat(composer.sentCount).isEqualTo(1)
        }
    }

    @Test
    fun `a sticker that fails to send offers a retry`() = runTest {
        val snackbarDispatcher = SnackbarDispatcher()
        createPresenter(
            snackbarDispatcher = snackbarDispatcher,
            stickerSender = FakeStickerSender(sendResult = { _, _ -> Result.failure(Exception("offline")) }),
        ).test {
            awaitEnabled().panelSink(PanelEvent.SendSticker(cat))
            consumeItemsUntilPredicate { cat.url in it.asDefault().sendingStickerUrls }
            consumeItemsUntilPredicate { it.asDefault().sendingStickerUrls.isEmpty() }
            val message = snackbarDispatcher.snackbarMessage.first()!!
            assertThat(message.messageResId).isEqualTo(R.string.gua_sticker_send_failed)
            assertThat(message.actionResId).isEqualTo(CommonStrings.action_retry)
        }
    }

    @Test
    fun `without a key the GIF tab only says GIFs are unavailable`() = runTest {
        createPresenter(gifProvider = FakeGifProvider(isAvailable = false)).test {
            val state = awaitEnabled()
            state.eventSink(ExpressionPanelEvent.Open)
            state.panelSink(PanelEvent.SelectTab(ExpressionTab.Gif))
            assertThat(consumeItemsUntilPredicate { it.asDefault().gifContent == GifContent.Unavailable }.last().asDefault().gifContent)
                .isEqualTo(GifContent.Unavailable)
        }
    }

    @Test
    fun `trending loads first, then pages while there is more`() = runTest {
        val pages = mutableListOf<Int>()
        val provider = FakeGifProvider(trendingResult = { page ->
            pages += page
            Result.success(GifPage(items = listOf(aGif("g$page")), page = page, hasNext = page < 2))
        })
        createPresenter(gifProvider = provider).test {
            openGifTab()
            val loaded = consumeItemsUntilPredicate { it.asDefault().gifContent is GifContent.Loaded }.last().asDefault()
            loaded.panelSink(PanelEvent.LoadMoreGifs)
            val more = consumeItemsUntilPredicate { (it.asDefault().gifContent as? GifContent.Loaded)?.items?.size == 2 }.last().asDefault()
            more.panelSink(PanelEvent.LoadMoreGifs)
            consumeItemsUntilTimeout()
            assertThat(pages).containsExactly(1, 2).inOrder()
            assertThat((more.gifContent as GifContent.Loaded).items.map { it.id }).containsExactly("g1", "g2").inOrder()
        }
    }

    @Test
    fun `a search waits for typing to stop and sends only the last query`() = runTest {
        val queries = mutableListOf<String>()
        val provider = FakeGifProvider(
            trendingResult = { Result.success(GifPage(emptyList(), 1, false)) },
            searchResult = { query, page ->
                queries += query
                Result.success(GifPage(listOf(aGif(query)), page, false))
            },
        )
        createPresenter(gifProvider = provider).test {
            val state = openGifTab()
            state.panelSink(PanelEvent.UpdateQuery("c"))
            state.panelSink(PanelEvent.UpdateQuery("ca"))
            state.panelSink(PanelEvent.UpdateQuery("cat"))
            val loaded = consumeItemsUntilPredicate { (it.asDefault().gifContent as? GifContent.Loaded)?.items?.firstOrNull()?.id == "cat" }.last()
            assertThat(queries).containsExactly("cat")
            assertThat(loaded.asDefault().gifQuery).isEqualTo("cat")
        }
    }

    @Test
    fun `a GIF error can be tried again`() = runTest {
        var attempts = 0
        val provider = FakeGifProvider(trendingResult = {
            attempts++
            if (attempts == 1) Result.failure(Exception("offline")) else Result.success(GifPage(listOf(aGif("g")), 1, false))
        })
        createPresenter(gifProvider = provider).test {
            openGifTab()
            val failed = consumeItemsUntilPredicate { it.asDefault().gifContent == GifContent.Error }.last().asDefault()
            failed.panelSink(PanelEvent.RetryGifs)
            consumeItemsUntilPredicate { it.asDefault().gifContent is GifContent.Loaded }
            assertThat(attempts).isEqualTo(2)
        }
    }

    @Test
    fun `a GIF is sent with its caption and reply, then goes to recent GIFs`() = runTest {
        val composer = RecordingComposer(inReplyToEventId = AN_EVENT_ID)
        val recentGifs = FakeRecentGifsStore()
        val sends = mutableListOf<Triple<String, String?, EventId?>>()
        createPresenter(
            composer = composer,
            recentGifsStore = recentGifs,
            gifSender = FakeGifSender(sendResult = { item, caption, inReplyTo, _ ->
                sends += Triple(item.id, caption, inReplyTo)
                Result.success(Unit)
            }),
        ).test {
            val state = awaitEnabled()
            state.panelSink(PanelEvent.OpenGif(aGif("g1")))
            val preview = consumeItemsUntilPredicate { it.asDefault().overlay is ExpressionOverlay.GifPreview }.last()
            preview.panelSinkOf().invoke(PanelEvent.SendGif(aGif("g1"), "Morning"))
            val last = consumeItemsUntilPredicate { it.asDefault().overlay == null }.last().asDefault()
            assertThat(sends).containsExactly(Triple("g1", "Morning", AN_EVENT_ID))
            assertThat(recentGifs.recents.value).containsExactly("g1")
            assertThat(composer.sentCount).isEqualTo(1)
            assertThat(last.overlay).isNull()
        }
    }

    @Test
    fun `the timeline sticker sheet names the chat pack and saves the sticker`() = runTest {
        val added = mutableListOf<StickerImage>()
        createPresenter(
            packs = listOf(pack),
            stickerLibrarySetting = FakeStickerLibrarySetting(addResult = {
                added += it
                Result.success(Unit)
            }),
        ).test {
            awaitEnabled().eventSink(ExpressionPanelEvent.ShowTimelineSticker(wave))
            val sheet = consumeItemsUntilPredicate { (it.asDefault().overlay as? ExpressionOverlay.TimelineSticker)?.packName != null }.last().asDefault()
            assertThat((sheet.overlay as ExpressionOverlay.TimelineSticker).packName).isEqualTo("Cats")

            sheet.panelSink(PanelEvent.SaveSticker(wave))
            consumeItemsUntilPredicate { it.asDefault().overlay == null }
            consumeItemsUntilTimeout()
            assertThat(added).containsExactly(wave)
        }
    }

    @Test
    fun `deleting one of my stickers asks first`() = runTest {
        val deleted = mutableListOf<String>()
        createPresenter(stickerLibrarySetting = FakeStickerLibrarySetting(deleteResult = {
            deleted += it
            Result.success(Unit)
        })).test {
            awaitEnabled().panelSink(PanelEvent.RequestDeleteSticker("sdog"))
            val confirming = consumeItemsUntilPredicate { it.asDefault().overlay != null }.last().asDefault()
            assertThat(confirming.overlay).isEqualTo(ExpressionOverlay.ConfirmDelete("sdog"))
            assertThat(deleted).isEmpty()

            confirming.panelSink(PanelEvent.ConfirmDeleteSticker)
            consumeItemsUntilPredicate { it.asDefault().overlay == null }
            consumeItemsUntilTimeout()
            assertThat(deleted).containsExactly("sdog")
        }
    }

    @Test
    fun `an emoji goes into the composer`() = runTest {
        val composer = RecordingComposer()
        createPresenter(composer = composer).test {
            awaitEnabled().panelSink(PanelEvent.InsertEmoji("🐈"))
            consumeItemsUntilTimeout()
            assertThat(composer.inserted).containsExactly("🐈")
        }
    }

    @Test
    fun `a pasted picture keeps its old path while a keyboard GIF is sent as a GIF`() = runTest {
        var fallbacks = 0
        val sentFiles = mutableListOf<File>()
        val copy = File.createTempFile("keyboard", ".gif")
        createPresenter(
            imageFiles = FakeImageFiles(copyResult = { copy }),
            gifSender = FakeGifSender(sendFileResult = { file, _, _ ->
                sentFiles += file
                Result.success(Unit)
            }),
        ).test {
            val state = awaitEnabled()
            state.eventSink(ExpressionPanelEvent.ReceiveKeyboardContent(aKeyboardContent("image/png", isFromInputMethod = false)) { fallbacks++ })
            state.eventSink(ExpressionPanelEvent.ReceiveKeyboardContent(aKeyboardContent("image/gif")) { fallbacks++ })
            consumeItemsUntilTimeout()
            assertThat(fallbacks).isEqualTo(1)
            assertThat(sentFiles).containsExactly(copy)
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<ExpressionPanelState>.awaitEnabled(): DefaultExpressionPanelState =
        consumeItemsUntilPredicate { it.isEnabled }.last().asDefault()

    private suspend fun app.cash.turbine.TurbineTestContext<ExpressionPanelState>.openGifTab(): DefaultExpressionPanelState {
        val state = awaitEnabled()
        state.eventSink(ExpressionPanelEvent.Open)
        state.panelSink(PanelEvent.SelectTab(ExpressionTab.Gif))
        return consumeItemsUntilPredicate { it.isOpen && it.asDefault().tab == ExpressionTab.Gif }.last().asDefault()
    }

    private fun ExpressionPanelState.asDefault() = this as DefaultExpressionPanelState

    private fun ExpressionPanelState.panelSinkOf() = asDefault().panelSink

    private fun aKeyboardContent(mimeType: String, isFromInputMethod: Boolean = true) =
        KeyboardContent(uri = Uri.parse("content://keyboard/1"), mimeType = mimeType, isFromInputMethod = isFromInputMethod)

    private fun aGif(id: String) = GifItem(
        id = id,
        title = id,
        renditions = mapOf(GifSize.Sm to GifRendition(animated = GifFile("https://static.klipy.com/$id.webp", 200, 100, 1000), still = null)),
    )

    private class RecordingComposer(override val inReplyToEventId: EventId? = null) : ExpressionComposer {
        override val timelineMode: Timeline.Mode = Timeline.Mode.Live
        var sentCount = 0
        val inserted = mutableListOf<String>()

        override suspend fun stickerRelation(): StickerRelation = inReplyToEventId?.let(StickerRelation::Reply) ?: StickerRelation.None

        override fun insertEmoji(emoji: String) {
            inserted += emoji
        }

        override fun onSent() {
            sentCount++
        }
    }

    private fun TestScope.createPresenter(
        isEnabled: Boolean = true,
        composer: ExpressionComposer = RecordingComposer(),
        recents: List<StickerImage> = emptyList(),
        library: StickerLibrary = StickerLibrary.EMPTY,
        packs: List<StickerPack> = emptyList(),
        recentStickersStore: FakeRecentStickersStore = FakeRecentStickersStore(MutableStateFlow(recents)),
        recentGifsStore: FakeRecentGifsStore = FakeRecentGifsStore(),
        stickerLibrarySetting: FakeStickerLibrarySetting = FakeStickerLibrarySetting(library = MutableStateFlow(library)),
        stickerSender: FakeStickerSender = FakeStickerSender(),
        gifSender: FakeGifSender = FakeGifSender(),
        gifProvider: FakeGifProvider = FakeGifProvider(trendingResult = { Result.success(GifPage(emptyList(), 1, false)) }),
        imageFiles: FakeImageFiles = FakeImageFiles(),
        panelStore: InMemoryExpressionPanelStore = InMemoryExpressionPanelStore(),
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ) = DefaultExpressionPanelPresenter(
        composer = composer,
        room = FakeJoinedRoom(),
        featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.StickersAndGifs.key to isEnabled)),
        stickerLibrarySetting = stickerLibrarySetting,
        gifLibrarySetting = FakeGifLibrarySetting(),
        recentStickersStore = recentStickersStore,
        recentGifsStore = recentGifsStore,
        stickerSender = stickerSender,
        gifSender = gifSender,
        gifProvider = gifProvider,
        stickerPackReader = FakeStickerPackReader { Result.success(packs) },
        emojiPickerPresenterFactory = fakeEmojiPickerPresenterFactory(),
        getRecentEmojis = { Result.success(kotlinx.collections.immutable.persistentListOf()) },
        addRecentEmoji = { Result.success(Unit) },
        pickerProvider = FakePickerProvider(),
        editorEngine = FakeStickerEditorEngine(),
        keyboardStickers = KeyboardStickers(
            imageFiles = imageFiles,
            stickerSender = stickerSender,
            gifSender = gifSender,
            encoder = StickerImageEncoder(),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        ),
        panelStore = panelStore,
        snackbarDispatcher = snackbarDispatcher,
        sessionCoroutineScope = backgroundScope,
    )
}
