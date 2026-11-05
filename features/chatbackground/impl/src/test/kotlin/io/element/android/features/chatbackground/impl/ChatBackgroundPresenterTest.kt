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
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundPhotoProcessor
import io.element.android.libraries.chatbackground.test.FakeChatBackgroundService
import io.element.android.libraries.chatbackground.test.aChatBackgroundPhotoUpload
import io.element.android.libraries.matrix.api.media.MediaPreviewConfig
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.room.CHAT_BACKGROUND_EVENT_TYPE
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.media.FakeMediaPreviewService
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChatBackgroundPresenterTest : RobolectricTest() {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val aPhotoUri: Uri = Uri.parse("content://photos/1")

    @Test
    fun `the screen starts on the chat's current background`() = runTest {
        val presenter = createPresenter(service = FakeChatBackgroundService(ChatBackground.Preset(ChatBackgroundPreset.Sky)))

        presenter.test {
            val state = consumeItemsUntilPredicate { it.current != ChatBackground.None && it.canChange }.last()
            assertThat(state.selection).isEqualTo(ChatBackground.Preset(ChatBackgroundPreset.Sky))
            assertThat(state.canSet).isFalse()
            assertThat(state.canRemove).isTrue()
        }
    }

    @Test
    fun `the photo is hidden while the chat hides media`() = runTest {
        val mediaPreviewConfig = MutableStateFlow(MediaPreviewConfig.DEFAULT)
        val presenter = createPresenter(matrixClient = FakeMatrixClient(mediaPreviewService = FakeMediaPreviewService(mediaPreviewConfig)))

        presenter.test {
            assertThat(awaitItem().isPhotoHidden).isFalse()
            mediaPreviewConfig.value = MediaPreviewConfig(mediaPreviewValue = MediaPreviewValue.Off, hideInviteAvatar = false)
            assertThat(consumeItemsUntilPredicate { it.isPhotoHidden }.last().isPhotoHidden).isTrue()
        }
    }

    @Test
    fun `selecting a preset and setting it sends it and finishes`() = runTest {
        val service = FakeChatBackgroundService()
        val presenter = createPresenter(service = service)

        presenter.test {
            val initial = consumeItemsUntilPredicate { it.canChange }.last()
            assertThat(initial.canSet).isFalse()
            assertThat(initial.canRemove).isFalse()
            initial.eventSink(ChatBackgroundEvent.SelectPreset(ChatBackgroundPreset.Aurora))
            val selected = awaitItem()
            assertThat(selected.selection).isEqualTo(ChatBackground.Preset(ChatBackgroundPreset.Aurora))
            assertThat(selected.canSet).isTrue()
            selected.eventSink(ChatBackgroundEvent.Apply)
            val done = consumeItemsUntilPredicate { it.action is AsyncAction.Success }.last()
            assertThat(done.operation).isEqualTo(ChatBackgroundOperation.Set)
        }
        assertThat(service.sentBackgrounds).containsExactly(ChatBackground.Preset(ChatBackgroundPreset.Aurora))
    }

    @Test
    fun `removing sends no background`() = runTest {
        val service = FakeChatBackgroundService(ChatBackground.Preset(ChatBackgroundPreset.Mint))
        val presenter = createPresenter(service = service)

        presenter.test {
            consumeItemsUntilPredicate { it.canRemove }.last().eventSink(ChatBackgroundEvent.Remove)
            val done = consumeItemsUntilPredicate { it.action is AsyncAction.Success }.last()
            assertThat(done.operation).isEqualTo(ChatBackgroundOperation.Remove)
        }
        assertThat(service.sentBackgrounds).containsExactly(ChatBackground.None)
    }

    @Test
    fun `a chosen photo is previewed, adjusted, uploaded and set`() = runTest {
        val service = FakeChatBackgroundService()
        val upload = aChatBackgroundPhotoUpload(size = 1_000)
        val matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }).apply {
            givenUploadMediaResult(Result.success("mxc://gua.global/uploaded"))
        }
        val pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) }
        val presenter = createPresenter(
            service = service,
            matrixClient = matrixClient,
            pickerProvider = pickerProvider,
            photoProcessor = FakeChatBackgroundPhotoProcessor { uri ->
                assertThat(uri).isEqualTo(aPhotoUri)
                Result.success(upload)
            },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatBackgroundEvent.ChoosePhoto)
            val previewing = consumeItemsUntilPredicate { it.photoDraft != null && it.action == AsyncAction.Uninitialized }.last()
            assertThat(previewing.photoDraft?.dim).isEqualTo(ChatBackground.Photo.DEFAULT_DIM)
            previewing.eventSink(ChatBackgroundEvent.SetDarkPreview(false))
            consumeItemsUntilPredicate { it.photoDraft?.isDarkPreview == false }
            previewing.eventSink(ChatBackgroundEvent.SetPhotoDim(60))
            val dimmed = consumeItemsUntilPredicate { it.photoDraft?.dim == 60 }.last()
            assertWithMessage("The dim only shows in the dark preview").that(dimmed.photoDraft?.isDarkPreview).isTrue()
            previewing.eventSink(ChatBackgroundEvent.SetPhotoAlign(-1000, 250))
            val adjusted = consumeItemsUntilPredicate { it.photoDraft?.alignY == 250 }.last()
            assertThat(adjusted.photoDraft?.alignX).isEqualTo(0)
            adjusted.eventSink(ChatBackgroundEvent.Apply)
            consumeItemsUntilPredicate { it.action is AsyncAction.Success }
        }
        assertThat(service.sentBackgrounds).containsExactly(
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
            )
        )
    }

    @Test
    fun `discarding the photo goes back to the picker`() = runTest {
        val pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) }
        val presenter = createPresenter(
            matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }),
            pickerProvider = pickerProvider,
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatBackgroundEvent.ChoosePhoto)
            consumeItemsUntilPredicate { it.photoDraft != null }.last().eventSink(ChatBackgroundEvent.DiscardPhoto)
            assertThat(consumeItemsUntilPredicate { it.photoDraft == null }.last().selection).isEqualTo(ChatBackground.None)
        }
    }

    @Test
    fun `a photo over the upload limit is refused before the preview`() = runTest {
        val pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) }
        val presenter = createPresenter(
            matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(500) }),
            pickerProvider = pickerProvider,
            photoProcessor = FakeChatBackgroundPhotoProcessor { Result.success(aChatBackgroundPhotoUpload(size = 1_000)) },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatBackgroundEvent.ChoosePhoto)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat((failed.action as AsyncAction.Failure).error).isInstanceOf(PhotoTooLargeException::class.java)
            assertThat(failed.photoDraft).isNull()
            failed.eventSink(ChatBackgroundEvent.ClearError)
            assertThat(awaitItem().action).isEqualTo(AsyncAction.Uninitialized)
        }
    }

    @Test
    fun `a failed upload shows an error and sets nothing`() = runTest {
        val service = FakeChatBackgroundService()
        val matrixClient = FakeMatrixClient(getMaxUploadSizeResult = { Result.success(10_000) }).apply {
            givenUploadMediaResult(Result.failure(IllegalStateException("upload failed")))
        }
        val presenter = createPresenter(
            service = service,
            matrixClient = matrixClient,
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
        )

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatBackgroundEvent.ChoosePhoto)
            consumeItemsUntilPredicate { it.photoDraft != null && it.action == AsyncAction.Uninitialized }.last().eventSink(ChatBackgroundEvent.Apply)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat(failed.photoDraft).isNotNull()
        }
        assertThat(service.sentBackgrounds).isEmpty()
    }

    @Test
    fun `a failed send keeps the screen open with an error`() = runTest {
        val presenter = createPresenter(service = FakeChatBackgroundService(setResult = { Result.failure(IllegalStateException("403")) }))

        presenter.test {
            consumeItemsUntilPredicate { it.canChange }.last().eventSink(ChatBackgroundEvent.SelectPreset(ChatBackgroundPreset.Night))
            awaitItem().eventSink(ChatBackgroundEvent.Apply)
            val failed = consumeItemsUntilPredicate { it.action is AsyncAction.Failure }.last()
            assertThat(failed.current).isEqualTo(ChatBackground.None)
        }
    }

    @Test
    fun `without permission nothing can be set or removed`() = runTest {
        val service = FakeChatBackgroundService(ChatBackground.Preset(ChatBackgroundPreset.Sky))
        var processedPhotos = 0
        val presenter = createPresenter(
            service = service,
            pickerProvider = FakePickerProvider().apply { givenResult(aPhotoUri) },
            photoProcessor = FakeChatBackgroundPhotoProcessor {
                processedPhotos++
                Result.success(aChatBackgroundPhotoUpload())
            },
            canSendBackground = false,
        )

        presenter.test {
            val state = awaitItem()
            assertThat(state.current).isEqualTo(ChatBackground.Preset(ChatBackgroundPreset.Sky))
            assertThat(state.canChange).isFalse()
            assertThat(state.canRemove).isFalse()
            state.eventSink(ChatBackgroundEvent.SelectPreset(ChatBackgroundPreset.Night))
            val selected = awaitItem()
            assertThat(selected.canSet).isFalse()
            selected.eventSink(ChatBackgroundEvent.Apply)
            selected.eventSink(ChatBackgroundEvent.Remove)
            selected.eventSink(ChatBackgroundEvent.ChoosePhoto)
            expectNoEvents()
        }
        assertThat(service.sentBackgrounds).isEmpty()
        assertThat(processedPhotos).isEqualTo(0)
    }

    private fun createPresenter(
        service: FakeChatBackgroundService = FakeChatBackgroundService(),
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        pickerProvider: FakePickerProvider = FakePickerProvider(),
        photoProcessor: FakeChatBackgroundPhotoProcessor = FakeChatBackgroundPhotoProcessor(),
        canSendBackground: Boolean = true,
    ) = ChatBackgroundPresenter(
        room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                roomPermissions = FakeRoomPermissions(
                    canSendState = { it == StateEventType.Custom(CHAT_BACKGROUND_EVENT_TYPE) && canSendBackground },
                ),
            ),
        ),
        matrixClient = matrixClient,
        chatBackgroundService = service,
        photoProcessor = photoProcessor,
        mediaPickerProvider = pickerProvider,
    )
}
