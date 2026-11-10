/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundService
import io.element.android.libraries.chatbackground.test.FakeChatThemeSetting
import io.element.android.libraries.chatbackground.test.aChatBackgroundPhotoUpload
import io.element.android.libraries.matrix.api.media.MediaPreviewConfig
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.media.FakeMediaPreviewService
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.ui.chatbackground.aChatBackgroundPhoto
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChatThemePresenterTest : RobolectricTest() {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val aPhotoUri: Uri = Uri.parse("content://photos/1")
    private val auroraGreen = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Aurora), ChatBubbleColor.Green)
    private val sunsetPink = ChatTheme(ChatBackground.Preset(ChatBackgroundPreset.Sunset), ChatBubbleColor.Pink)

    @Test
    fun `chat mode starts on the chat's own theme and knows the default`() = runTest {
        val presenter = createChatPresenter(service = FakeChatBackgroundService(auroraGreen), setting = FakeChatThemeSetting(sunsetPink))

        presenter.test {
            val state = consumeItemsUntilPredicate { it.canChange }.last()
            assertThat(state.mode).isEqualTo(ChatThemeMode.Chat)
            assertThat(state.current).isEqualTo(auroraGreen)
            assertThat(state.defaultTheme).isEqualTo(sunsetPink)
            assertThat(state.defaultTile).isEqualTo(sunsetPink)
            assertThat(state.page).isEqualTo(ChatThemePage.Gallery)
            assertThat(state.preview).isNull()
        }
    }

    @Test
    fun `default mode edits the personal default and its first tile is the stock look`() = runTest {
        val presenter = createDefaultPresenter(setting = FakeChatThemeSetting(sunsetPink))

        presenter.test {
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(ChatThemeMode.Default)
            assertThat(state.current).isEqualTo(sunsetPink)
            assertThat(state.canChange).isTrue()
            assertThat(state.defaultTile).isEqualTo(ChatTheme.STOCK)
        }
    }

    @Test
    fun `choosing a tile previews it first, and saving it sets the chat theme and goes back to the gallery`() = runTest {
        val service = FakeChatBackgroundService()
        val presenter = createChatPresenter(service = service)

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.SelectTheme(auroraGreen))
            val previewing = awaitItem()
            assertThat(previewing.preview).isEqualTo(ChatThemePreview(shown = auroraGreen, saved = auroraGreen))
            assertThat(service.sentThemes).isEmpty()
            previewing.eventSink(ChatThemeEvent.SetDarkPreview(true))
            assertThat(awaitItem().preview?.isDarkPreview).isTrue()
            previewing.eventSink(ChatThemeEvent.Save)
            val saved = consumeItemsUntilPredicate { it.preview == null && it.current == auroraGreen }.last()
            assertThat(saved.action).isEqualTo(AsyncAction.Uninitialized)
            assertThat(saved.page).isEqualTo(ChatThemePage.Gallery)
        }
        assertThat(service.sentThemes).containsExactly(auroraGreen)
    }

    @Test
    fun `cancelling the preview saves nothing`() = runTest {
        val service = FakeChatBackgroundService()
        val presenter = createChatPresenter(service = service)

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.SelectTheme(auroraGreen))
            awaitItem().eventSink(ChatThemeEvent.Cancel)
            assertThat(awaitItem().preview).isNull()
        }
        assertThat(service.sentThemes).isEmpty()
    }

    @Test
    fun `the Default tile of a chat previews the personal default and removes the chat's theme`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        val presenter = createChatPresenter(service = service, setting = FakeChatThemeSetting(sunsetPink))

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.SelectDefault)
            val previewing = awaitItem()
            assertThat(previewing.preview).isEqualTo(ChatThemePreview(shown = sunsetPink, saved = ChatTheme.STOCK))
            previewing.eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.current == ChatTheme.STOCK && it.preview == null }
        }
        assertThat(service.sentThemes).containsExactly(ChatTheme.STOCK)
    }

    @Test
    fun `the Classic tile of the default previews the stock look and clears the default`() = runTest {
        val setting = FakeChatThemeSetting(sunsetPink)
        val presenter = createDefaultPresenter(setting = setting)

        presenter.test {
            awaitItem().eventSink(ChatThemeEvent.SelectDefault)
            val previewing = awaitItem()
            assertThat(previewing.preview).isEqualTo(ChatThemePreview(shown = ChatTheme.STOCK, saved = ChatTheme.STOCK))
            previewing.eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.current == ChatTheme.STOCK && it.preview == null }
        }
        assertThat(setting.sentThemes).containsExactly(ChatTheme.STOCK)
    }

    @Test
    fun `a tile saved in default mode sets the personal default`() = runTest {
        val setting = FakeChatThemeSetting()
        val presenter = createDefaultPresenter(setting = setting)

        presenter.test {
            awaitItem().eventSink(ChatThemeEvent.SelectTheme(sunsetPink))
            awaitItem().eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.current == sunsetPink && it.preview == null }
        }
        assertThat(setting.sentThemes).containsExactly(sunsetPink)
    }

    @Test
    fun `the bubble picker starts on the current bubble and saves the chosen one with the wallpaper unchanged`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        val presenter = createChatPresenter(service = service)

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenBubblePicker)
            val picking = awaitItem()
            assertThat(picking.page).isEqualTo(ChatThemePage.BubblePicker(selection = ChatBubbleColor.Green))
            picking.eventSink(ChatThemeEvent.SelectBubble(null))
            val standard = awaitItem()
            assertThat(standard.page).isEqualTo(ChatThemePage.BubblePicker(selection = null))
            standard.eventSink(ChatThemeEvent.SelectBubble(ChatBubbleColor.Purple))
            awaitItem().eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.page == ChatThemePage.Gallery && it.current == auroraGreen.copy(bubble = ChatBubbleColor.Purple) }
        }
        assertThat(service.sentThemes).containsExactly(auroraGreen.copy(bubble = ChatBubbleColor.Purple))
    }

    @Test
    fun `a wallpaper is previewed with the current bubble and saved with the bubble unchanged`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        val presenter = createChatPresenter(service = service)
        val dawn = ChatBackground.Preset(ChatBackgroundPreset.Dawn)

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenWallpaperPicker)
            val picking = awaitItem()
            assertThat(picking.page).isEqualTo(ChatThemePage.WallpaperPicker)
            picking.eventSink(ChatThemeEvent.SelectWallpaper(dawn))
            val previewing = awaitItem()
            assertThat(previewing.preview?.shown).isEqualTo(ChatTheme(dawn, ChatBubbleColor.Green))
            previewing.eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.page == ChatThemePage.Gallery && it.preview == null && it.current == ChatTheme(dawn, ChatBubbleColor.Green) }
        }
        assertThat(service.sentThemes).containsExactly(ChatTheme(dawn, ChatBubbleColor.Green))
    }

    @Test
    fun `no wallpaper keeps a coloured bubble, and without one it removes the theme`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        val presenter = createChatPresenter(service = service)

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenWallpaperPicker)
            awaitItem().eventSink(ChatThemeEvent.SelectWallpaper(ChatBackground.None))
            awaitItem().eventSink(ChatThemeEvent.Save)
            consumeItemsUntilPredicate { it.current == ChatTheme(bubble = ChatBubbleColor.Green) }
        }
        assertThat(service.sentThemes).containsExactly(ChatTheme(bubble = ChatBubbleColor.Green))
    }

    @Test
    fun `cancel closes the preview, then the picker`() = runTest {
        val presenter = createChatPresenter()

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenWallpaperPicker)
            awaitItem().eventSink(ChatThemeEvent.SelectWallpaper(ChatBackground.Preset(ChatBackgroundPreset.Mint)))
            awaitItem().eventSink(ChatThemeEvent.Cancel)
            val back = awaitItem()
            assertThat(back.preview).isNull()
            assertThat(back.page).isEqualTo(ChatThemePage.WallpaperPicker)
            back.eventSink(ChatThemeEvent.Cancel)
            assertThat(awaitItem().page).isEqualTo(ChatThemePage.Gallery)
        }
    }

    @Test
    fun `a chosen photo is previewed, adjusted, uploaded and saved with the bubble unchanged`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        val upload = aChatBackgroundPhotoUpload(size = 1_000)
        val matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }).apply {
            givenUploadMediaResult(Result.success("mxc://gua.global/uploaded"))
        }
        val presenter = createChatPresenter(
            service = service,
            matrixClient = matrixClient,
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
            photoProcessor = FakeChatBackgroundPhotoProcessor { uri ->
                assertThat(uri).isEqualTo(aPhotoUri)
                Result.success(upload)
            },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenWallpaperPicker)
            awaitItem().eventSink(ChatThemeEvent.ChoosePhoto)
            val previewing = consumeItemsUntilPredicate { it.photoDraft != null && it.action == AsyncAction.Uninitialized }.last()
            assertThat(previewing.photoDraft?.dim).isEqualTo(ChatBackground.Photo.DEFAULT_DIM)
            previewing.eventSink(ChatThemeEvent.SetDarkPreview(false))
            consumeItemsUntilPredicate { it.photoDraft?.isDarkPreview == false }
            previewing.eventSink(ChatThemeEvent.SetPhotoDim(60))
            val dimmed = consumeItemsUntilPredicate { it.photoDraft?.dim == 60 }.last()
            assertWithMessage("The dim only shows in the dark preview").that(dimmed.photoDraft?.isDarkPreview).isTrue()
            previewing.eventSink(ChatThemeEvent.SetPhotoAlign(-1000, 250))
            val adjusted = consumeItemsUntilPredicate { it.photoDraft?.alignY == 250 }.last()
            assertThat(adjusted.photoDraft?.alignX).isEqualTo(0)
            adjusted.eventSink(ChatThemeEvent.Save)
            val saved = consumeItemsUntilPredicate { it.photoDraft == null && it.page == ChatThemePage.Gallery }.last()
            assertThat(saved.action).isEqualTo(AsyncAction.Uninitialized)
        }
        assertThat(service.sentThemes).containsExactly(
            ChatTheme(
                ChatBackground.Photo(
                    url = "mxc://gua.global/uploaded",
                    mimetype = "image/jpeg",
                    width = upload.width,
                    height = upload.height,
                    size = 1_000,
                    blurhash = upload.blurhash,
                    dim = 60,
                    alignX = 0,
                    alignY = 250,
                ),
                ChatBubbleColor.Green,
            )
        )
    }

    @Test
    fun `cancelling the photo goes back to the wallpaper picker`() = runTest {
        val presenter = createChatPresenter(
            matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }),
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.OpenWallpaperPicker)
            awaitItem().eventSink(ChatThemeEvent.ChoosePhoto)
            consumeItemsUntilPredicate { it.photoDraft != null }.last().eventSink(ChatThemeEvent.Cancel)
            val back = consumeItemsUntilPredicate { it.photoDraft == null }.last()
            assertThat(back.page).isEqualTo(ChatThemePage.WallpaperPicker)
        }
    }

    @Test
    fun `a photo over the upload limit is refused before the preview`() = runTest {
        val presenter = createChatPresenter(
            matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(500) }),
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
            photoProcessor = FakeChatBackgroundPhotoProcessor { Result.success(aChatBackgroundPhotoUpload(size = 1_000)) },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.ChoosePhoto)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat((failed.action as AsyncAction.Failure).error).isInstanceOf(PhotoTooLargeException::class.java)
            assertThat(failed.operation).isEqualTo(ChatThemeOperation.PreparePhoto)
            assertThat(failed.photoDraft).isNull()
            failed.eventSink(ChatThemeEvent.ClearError)
            assertThat(awaitItem().action).isEqualTo(AsyncAction.Uninitialized)
        }
    }

    @Test
    fun `a failed upload shows an error and saves nothing`() = runTest {
        val service = FakeChatBackgroundService()
        val matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }).apply {
            givenUploadMediaResult(Result.failure(IllegalStateException("upload failed")))
        }
        val presenter = createChatPresenter(
            service = service,
            matrixClient = matrixClient,
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.ChoosePhoto)
            consumeItemsUntilPredicate { it.photoDraft != null && it.action == AsyncAction.Uninitialized }.last().eventSink(ChatThemeEvent.Save)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat(failed.photoDraft).isNotNull()
        }
        assertThat(service.sentThemes).isEmpty()
    }

    @Test
    fun `a failed save keeps the preview open with an error`() = runTest {
        val presenter = createChatPresenter(service = FakeChatBackgroundService(setResult = { Result.failure(IllegalStateException("403")) }))

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatThemeEvent.SelectTheme(sunsetPink))
            awaitItem().eventSink(ChatThemeEvent.Save)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat(failed.operation).isEqualTo(ChatThemeOperation.Save)
            assertThat(failed.preview?.saved).isEqualTo(sunsetPink)
            assertThat(failed.current).isEqualTo(ChatTheme.STOCK)
        }
    }

    @Test
    fun `without permission nothing opens or saves`() = runTest {
        val service = FakeChatBackgroundService(auroraGreen)
        var processedPhotos = 0
        val presenter = createChatPresenter(
            service = service,
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
            photoProcessor = FakeChatBackgroundPhotoProcessor {
                processedPhotos++
                Result.success(aChatBackgroundPhotoUpload())
            },
            canChange = false,
        )

        presenter.test {
            val state = awaitItem()
            assertThat(state.current).isEqualTo(auroraGreen)
            assertThat(state.canChange).isFalse()
            state.eventSink(ChatThemeEvent.SelectTheme(sunsetPink))
            state.eventSink(ChatThemeEvent.SelectDefault)
            state.eventSink(ChatThemeEvent.OpenBubblePicker)
            state.eventSink(ChatThemeEvent.OpenWallpaperPicker)
            state.eventSink(ChatThemeEvent.SelectWallpaper(ChatBackground.None))
            state.eventSink(ChatThemeEvent.ChoosePhoto)
            state.eventSink(ChatThemeEvent.Save)
            expectNoEvents()
        }
        assertThat(service.sentThemes).isEmpty()
        assertThat(processedPhotos).isEqualTo(0)
    }

    @Test
    fun `a chat photo is hidden in a public room while media is off, and shown in a private chat`() = runTest {
        val photo = ChatTheme(aChatBackgroundPhoto(), ChatBubbleColor.Blue)
        val mediaOff = MediaPreviewConfig(mediaPreviewValue = MediaPreviewValue.Off, hideInviteAvatar = false)

        createChatPresenter(service = FakeChatBackgroundService(photo), joinRule = JoinRule.Public, mediaPreviewConfig = mediaOff).test {
            val state = consumeItemsUntilPredicate { it.canChange }.last()
            assertThat(state.isPhotoHidden).isTrue()
            assertThat(state.shownCurrent).isEqualTo(ChatTheme(bubble = ChatBubbleColor.Blue))
        }
        createChatPresenter(service = FakeChatBackgroundService(photo), joinRule = JoinRule.Invite, mediaPreviewConfig = mediaOff).test {
            val state = consumeItemsUntilPredicate { it.canChange }.last()
            assertThat(state.isPhotoHidden).isFalse()
            assertThat(state.shownCurrent).isEqualTo(photo)
        }
    }

    private fun createChatPresenter(
        service: FakeChatBackgroundService = FakeChatBackgroundService(),
        setting: FakeChatThemeSetting = FakeChatThemeSetting(),
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        pickerProvider: FakePickerProvider = FakePickerProvider(),
        photoProcessor: FakeChatBackgroundPhotoProcessor = FakeChatBackgroundPhotoProcessor(),
        canChange: Boolean = true,
        joinRule: JoinRule = JoinRule.Public,
        mediaPreviewConfig: MediaPreviewConfig = MediaPreviewConfig.DEFAULT,
    ) = ChatThemePresenter(
        target = RoomChatThemeTarget(
            room = FakeJoinedRoom(
                baseRoom = FakeBaseRoom(
                    initialRoomInfo = aRoomInfo(joinRule = joinRule),
                    roomPermissions = FakeRoomPermissions(
                        canSendState = { it == StateEventType.Custom(CHAT_BACKGROUND_EVENT_TYPE) && canChange },
                    ),
                ),
            ),
            chatBackgroundService = service,
            chatThemeSetting = setting,
            mediaPreviewService = FakeMediaPreviewService(MutableStateFlow(mediaPreviewConfig)),
        ),
        matrixClient = matrixClient,
        photoProcessor = photoProcessor,
        mediaPickerProvider = pickerProvider,
    )

    private fun createDefaultPresenter(
        setting: FakeChatThemeSetting = FakeChatThemeSetting(),
    ) = ChatThemePresenter(
        target = PersonalDefaultChatThemeTarget(setting),
        matrixClient = FakeMatrixClient(),
        photoProcessor = FakeChatBackgroundPhotoProcessor(),
        mediaPickerProvider = FakePickerProvider(),
    )
}
