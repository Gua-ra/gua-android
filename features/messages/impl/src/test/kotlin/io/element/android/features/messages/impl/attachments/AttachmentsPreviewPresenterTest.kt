/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.messages.impl.attachments

import android.net.Uri
import androidx.core.net.toUri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.attachments.preview.AttachmentsPreviewEvent
import io.element.android.features.messages.impl.attachments.preview.AttachmentsPreviewPresenter
import io.element.android.features.messages.impl.attachments.preview.OnDoneListener
import io.element.android.features.messages.impl.attachments.preview.SendActionState
import io.element.android.features.messages.impl.attachments.preview.aMediaUploadInfo
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEditor
import io.element.android.features.messages.impl.attachments.preview.imageeditor.AttachmentImageEdits
import io.element.android.features.messages.impl.attachments.preview.imageeditor.EditedLocalMedia
import io.element.android.features.messages.impl.attachments.preview.imageeditor.NormalizedCropRect
import io.element.android.features.messages.impl.attachments.preview.imageeditor.assertIsSimilarTo
import io.element.android.features.messages.impl.attachments.video.MediaOptimizationSelectorState
import io.element.android.features.messages.impl.attachments.video.VideoCompressionPresetSelector
import io.element.android.features.messages.impl.attachments.video.VideoUploadEstimation
import io.element.android.features.messages.impl.fixtures.aMediaAttachment
import io.element.android.features.messages.impl.mediastack.MediaBatch
import io.element.android.features.messages.impl.mediastack.PendingMediaBatches
import io.element.android.features.messages.test.attachments.video.FakeMediaOptimizationSelectorPresenterFactory
import io.element.android.libraries.androidutils.file.TemporaryUriDeleter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.TransactionId
import io.element.android.libraries.matrix.api.media.AudioInfo
import io.element.android.libraries.matrix.api.media.FileInfo
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.media.MediaUploadHandler
import io.element.android.libraries.matrix.api.media.VideoInfo
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_CAPTION
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.media.FakeMediaUploadHandler
import io.element.android.libraries.matrix.test.permalink.FakePermalinkBuilder
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.impl.DefaultMediaSender
import io.element.android.libraries.mediaupload.test.FakeMediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.mediaviewer.api.aVideoMediaInfo
import io.element.android.libraries.mediaviewer.api.anApkMediaInfo
import io.element.android.libraries.mediaviewer.api.anImageMediaInfo
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import io.element.android.libraries.mediaviewer.test.viewer.aLocalMedia
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.fake.FakeTemporaryUriDeleter
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempFile

class AttachmentsPreviewPresenterTest : RobolectricTest() {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val mockMediaUrl: Uri = mockk("localMediaUri") {
        every { path } returns "/path/to/media"
    }

    @Test
    fun `present - initial state`() = runTest {
        createAttachmentsPreviewPresenter().test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
        }
    }

    @Test
    fun `present - send media success scenario`() = runTest {
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val mediaPreProcessor = FakeMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = true))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Uploading(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendFileResult.assertions().isCalledOnce()
            onDoneListener.assertions().isCalledOnce()
            assertThat(mediaPreProcessor.cleanUpCallCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - send media after pre-processing success scenario`() = runTest {
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val processLatch = CompletableDeferred<Unit>()
        val mediaPreProcessor = FakeMediaPreProcessor(processLatch)
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            // Pre-processing finishes
            processLatch.complete(Unit)
            advanceUntilIdle()
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Uploading(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendFileResult.assertions().isCalledOnce()
            onDoneListener.assertions().isCalledOnce()
            assertThat(mediaPreProcessor.cleanUpCallCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - send media before pre-processing success scenario`() = runTest {
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val processLatch = CompletableDeferred<Unit>()
        val mediaPreProcessor = FakeMediaPreProcessor(processLatch)
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            // Pre-processing finishes
            processLatch.complete(Unit)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = true))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Uploading(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendFileResult.assertions().isCalledOnce()
            onDoneListener.assertions().isCalledOnce()
            assertThat(mediaPreProcessor.cleanUpCallCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - send media with pre-processing failure after user sends media`() = runTest {
        val room = FakeJoinedRoom()
        val onDoneListener = lambdaRecorder<Unit> { }
        val processLatch = CompletableDeferred<Unit>()
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = FakeMediaPreProcessor().apply {
                givenResult(Result.failure(Exception()))
            },
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            // Pre-processing finishes
            processLatch.complete(Unit)
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Failure::class.java)
        }
    }

    @Test
    fun `present - send media with pre-processing failure before user sends media`() = runTest {
        val room = FakeJoinedRoom()
        val onDoneListener = lambdaRecorder<Unit> { }
        val processLatch = CompletableDeferred<Unit>()
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = FakeMediaPreProcessor().apply {
                givenResult(Result.failure(Exception()))
            },
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            // Pre-processing finishes
            processLatch.complete(Unit)
            advanceUntilIdle()
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Failure::class.java)
        }
    }

    @Test
    fun `present - cancel scenario`() = runTest {
        val onDoneListener = lambdaRecorder<Unit> { }
        val deleteCallback = lambdaRecorder<Uri?, Unit> {}
        val mediaPreProcessor = FakeMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            mediaPreProcessor = mediaPreProcessor,
            temporaryUriDeleter = FakeTemporaryUriDeleter(deleteCallback),
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.eventSink(AttachmentsPreviewEvent.CancelAndDismiss)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            deleteCallback.assertions().isCalledOnce()
            onDoneListener.assertions().isCalledOnce()
            assertThat(mediaPreProcessor.cleanUpCallCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - send image with caption success scenario`() = runTest {
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId?, _: String? ->
                Result.success(FakeMediaUploadHandler())
            }
        val mediaPreProcessor = FakeMediaPreProcessor().apply {
            givenImageResult()
        }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendImageLambda = sendImageResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.ReadyToUpload::class.java)
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.Uploading::class.java)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendImageResult.assertions().isCalledOnce().with(
                any(),
                any(),
                any(),
                value(A_CAPTION),
                any(),
                any(),
                value(null),
            )
            onDoneListener.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - send video with caption success scenario`() = runTest {
        val sendVideoResult =
            lambdaRecorder { _: File, _: File?, _: VideoInfo, _: String?, _: String?, _: EventId?, _: String? ->
                Result.success(FakeMediaUploadHandler())
            }
        val mediaPreProcessor = FakeMediaPreProcessor().apply {
            givenVideoResult()
        }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendVideoLambda = sendVideoResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.ReadyToUpload::class.java)
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.Uploading::class.java)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendVideoResult.assertions().isCalledOnce().with(
                any(),
                any(),
                any(),
                value(A_CAPTION),
                any(),
                any(),
                value(null),
            )
            onDoneListener.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - send audio with caption success scenario`() = runTest {
        val sendAudioResult =
            lambdaRecorder<File, AudioInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.success(FakeMediaUploadHandler())
            }
        val mediaPreProcessor = FakeMediaPreProcessor().apply {
            givenAudioResult()
        }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendAudioLambda = sendAudioResult
            },
        )
        val onDoneListener = lambdaRecorder<Unit> { }
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.ReadyToUpload::class.java)
            assertThat(awaitItem().sendActionState).isInstanceOf(SendActionState.Sending.Uploading::class.java)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Done)
            sendAudioResult.assertions().isCalledOnce().with(
                any(),
                any(),
                value(A_CAPTION),
                any(),
                any(),
            )
            onDoneListener.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - send media failure scenario`() = runTest {
        val failure = MediaPreProcessor.Failure(null)
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { _, _, _, _, _ ->
                Result.failure(failure)
            }
        val onDoneListenerResult = lambdaRecorder<Unit> {}
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val presenter = createAttachmentsPreviewPresenter(room = room, onDoneListener = onDoneListenerResult)
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Uploading(listOf(mediaUploadInfo)))
            val failureState = awaitItem()
            assertThat(failureState.sendActionState).isEqualTo(SendActionState.Failure(failure, listOf(mediaUploadInfo)))
            sendFileResult.assertions().isCalledOnce()
            failureState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            val clearedState = awaitLastSequentialItem()
            assertThat(clearedState.sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
        }
    }

    @Test
    fun `present - dismissing the progress dialog stops media upload`() = runTest {
        val onDoneListenerResult = lambdaRecorder<Unit> {}
        val presenter = createAttachmentsPreviewPresenter(
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendFileLambda = { _, _, _, _, _ ->
                        Result.success(FakeMediaUploadHandler())
                    }
                }
            ),
            onDoneListener = onDoneListenerResult,
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.sendActionState).isEqualTo(SendActionState.Idle)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Processing(displayProgress = false))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.Uploading(listOf(mediaUploadInfo)))
            initialState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            assertThat(awaitItem().sendActionState).isEqualTo(SendActionState.Sending.ReadyToUpload(listOf(mediaUploadInfo)))
            // The sending is cancelled and the state is kept at ReadyToUpload
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `present - file too large will display error`() = runTest {
        val onDoneListenerResult = lambdaRecorder<Unit> {}

        val localMedia = aLocalMedia(uri = Uri.EMPTY, mediaInfo = anApkMediaInfo())
        val maxUploadSize = 999L // Set a max upload size smaller than the file size

        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(Attachment.Media(localMedia)),
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendFileLambda = { _, _, _, _, _ ->
                        Result.success(FakeMediaUploadHandler())
                    }
                }
            ),
            onDoneListener = onDoneListenerResult,
            mediaOptimizationSelectorPresenterFactory = FakeMediaOptimizationSelectorPresenterFactory {
                MediaOptimizationSelectorState(
                    index = 0,
                    // Set a max upload size smaller than the file size
                    maxUploadSize = AsyncData.Success(maxUploadSize),
                    videoSizeEstimations = AsyncData.Uninitialized,
                    isImageOptimizationEnabled = null,
                    selectedVideoPreset = null,
                    displayMediaSelectorViews = false,
                    displayVideoPresetSelectorDialog = false,
                    eventSink = {},
                )
            }
        )

        presenter.test {
            assertThat(localMedia.info.fileSize).isGreaterThan(maxUploadSize)

            consumeItemsUntilPredicate { it.mediaOptimizationSelectorState.maxUploadSize.isSuccess() }

            assertThat(awaitItem().displayFileTooLargeError).isTrue()
        }
    }

    @Test
    fun `present - video size estimations too large will display error`() = runTest {
        val onDoneListenerResult = lambdaRecorder<Unit> {}

        val localMedia = aLocalMedia(uri = Uri.EMPTY, mediaInfo = aVideoMediaInfo())

        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(Attachment.Media(localMedia)),
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendFileLambda = { _, _, _, _, _ ->
                        Result.success(FakeMediaUploadHandler())
                    }
                }
            ),
            onDoneListener = onDoneListenerResult,
            mediaOptimizationSelectorPresenterFactory = FakeMediaOptimizationSelectorPresenterFactory {
                MediaOptimizationSelectorState(
                    index = 0,
                    // Set a max upload size smaller than the file size
                    maxUploadSize = AsyncData.Success(Long.MAX_VALUE),
                    videoSizeEstimations = AsyncData.Success(
                        persistentListOf(
                            VideoUploadEstimation(
                                preset = VideoCompressionPreset.LOW,
                                // The important field is canUpload, it will normally be based on the sizeInBytes
                                canUpload = false,
                                sizeInBytes = 0L,
                            ),
                            VideoUploadEstimation(
                                preset = VideoCompressionPreset.STANDARD,
                                canUpload = false,
                                sizeInBytes = 0L,
                            ),
                            VideoUploadEstimation(
                                preset = VideoCompressionPreset.HIGH,
                                canUpload = false,
                                sizeInBytes = 0L,
                            ),
                        )
                    ),
                    isImageOptimizationEnabled = null,
                    selectedVideoPreset = null,
                    displayMediaSelectorViews = false,
                    displayVideoPresetSelectorDialog = false,
                    eventSink = {},
                )
            }
        )

        presenter.test {
            consumeItemsUntilPredicate {
                it.mediaOptimizationSelectorState.maxUploadSize.isSuccess() &&
                    it.mediaOptimizationSelectorState.videoSizeEstimations.dataOrNull()?.isNotEmpty() == true
            }

            assertThat(awaitItem().displayFileTooLargeError).isTrue()
        }
    }

    @Test
    fun `present - applying image edits updates the attachment`() = runTest {
        val editedUri = Uri.parse("file:///tmp/edited.jpeg")
        val presenter = createAttachmentsPreviewPresenter(
            displayMediaQualitySelectorViews = true,
            attachmentImageEditor = FakeAttachmentImageEditor {
                Result.success(
                    EditedLocalMedia(
                        localMedia = aLocalMedia(uri = editedUri),
                        file = File("/tmp/edited.jpeg"),
                    )
                )
            }
        )

        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = awaitItem()
            assertThat(editorState.imageEditorState).isNotNull()

            editorState.eventSink(AttachmentsPreviewEvent.RotateImageToTheLeft)
            val rotatedState = awaitItem()
            assertThat(rotatedState.imageEditorState?.edits?.rotationQuarterTurns).isEqualTo(3)

            rotatedState.eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
            assertThat(awaitItem().isApplyingImageEdits).isTrue()

            val appliedState = awaitItem()
            assertThat((appliedState.attachments.first() as Attachment.Media).localMedia.uri).isEqualTo(editedUri)
            assertThat(appliedState.imageEditorState).isNull()
            assertThat(appliedState.isApplyingImageEdits).isFalse()
        }
    }

    @Test
    fun `present - reopening image editor keeps original media and previous edits`() = runTest {
        val editedUri = Uri.parse("file:///tmp/edited.jpeg")
        val originalLocalMedia = aLocalMedia(uri = mockMediaUrl)
        val cropRect = NormalizedCropRect(
            left = 0.2f,
            top = 0.15f,
            right = 0.85f,
            bottom = 0.9f,
        )
        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(Attachment.Media(originalLocalMedia)),
            displayMediaQualitySelectorViews = true,
            attachmentImageEditor = FakeAttachmentImageEditor {
                Result.success(
                    EditedLocalMedia(
                        localMedia = aLocalMedia(uri = editedUri),
                        file = File("/tmp/edited.jpeg"),
                    )
                )
            }
        )

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()

            editorState.eventSink(AttachmentsPreviewEvent.UpdateImageCropRect(cropRect))
            val croppedState = awaitItem()
            croppedState.eventSink(AttachmentsPreviewEvent.RotateImageToTheLeft)
            val rotatedState = awaitItem()
            rotatedState.eventSink(AttachmentsPreviewEvent.FlipImageHorizontally)
            val flippedHorizontallyState = awaitItem()
            flippedHorizontallyState.eventSink(AttachmentsPreviewEvent.FlipImageVertically)
            val flippedState = awaitItem()
            flippedState.eventSink(AttachmentsPreviewEvent.ApplyImageEdits)

            val appliedState = consumeItemsUntilPredicate { !it.isApplyingImageEdits && it.imageEditorState == null }.last()
            assertThat((appliedState.attachments.first() as Attachment.Media).localMedia.uri).isEqualTo(editedUri)

            appliedState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val reopenedState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()
            assertThat(reopenedState.imageEditorState!!.localMedia.uri).isEqualTo(originalLocalMedia.uri)
            val rotatedCropRect = NormalizedCropRect(
                left = cropRect.top,
                top = 1f - cropRect.right,
                right = cropRect.bottom,
                bottom = 1f - cropRect.left,
            )
            val flippedCropRect = NormalizedCropRect(
                left = 1f - rotatedCropRect.right,
                top = 1f - rotatedCropRect.bottom,
                right = 1f - rotatedCropRect.left,
                bottom = 1f - rotatedCropRect.top,
            )
            reopenedState.imageEditorState.edits.cropRect.assertIsSimilarTo(flippedCropRect)
            assertThat(reopenedState.imageEditorState.edits.rotationQuarterTurns).isEqualTo(3)
            assertThat(reopenedState.imageEditorState.edits.rotationDegrees).isEqualTo(270)
            assertThat(reopenedState.imageEditorState.edits.isFlippedHorizontally).isTrue()
            assertThat(reopenedState.imageEditorState.edits.isFlippedVertically).isTrue()
        }
    }

    @Test
    fun `present - image editor flip events update edits`() = runTest {
        val presenter = createAttachmentsPreviewPresenter(displayMediaQualitySelectorViews = true)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()

            editorState.eventSink(AttachmentsPreviewEvent.FlipImageHorizontally)
            val flippedHorizontallyState = awaitItem()
            assertThat(flippedHorizontallyState.imageEditorState?.edits?.isFlippedHorizontally).isTrue()

            flippedHorizontallyState.eventSink(AttachmentsPreviewEvent.FlipImageVertically)
            val flippedState = awaitItem()
            assertThat(flippedState.imageEditorState?.edits?.isFlippedVertically).isTrue()
        }
    }

    @Test
    fun `present - sendAsFile attachment is pre-processed without image compression`() = runTest {
        // Even though the user has enabled "Optimize media quality" globally, picking the file
        // through the Files picker (sendAsFile = true) must skip compression. Regression test
        // for https://github.com/element-hq/element-x-android/issues/6365
        val mediaPreProcessor = FakeMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(
                Attachment.Media(
                    localMedia = aLocalMedia(mockMediaUrl, anImageMediaInfo()),
                    sendAsFile = true,
                )
            ),
            mediaPreProcessor = mediaPreProcessor,
            // Selector views are hidden in the sendAsFile flow, which triggers the auto pre-process path.
            displayMediaQualitySelectorViews = false,
            mediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(
                config = MediaOptimizationConfig(
                    compressImages = false,
                    videoCompressionPreset = VideoCompressionPreset.HIGH,
                )
            ),
        )

        presenter.test {
            consumeItemsUntilPredicate { mediaPreProcessor.processCallCount > 0 }
            assertThat(mediaPreProcessor.lastMediaOptimizationConfig).isEqualTo(
                MediaOptimizationConfig(
                    compressImages = false,
                    videoCompressionPreset = VideoCompressionPreset.HIGH,
                )
            )
        }
    }

    @Test
    fun `present - sending edited media keeps the edited file available until upload starts`() = runTest {
        val editedFile = createTempFile(suffix = ".jpeg").toFile().apply {
            writeText("edited-media")
        }
        val sendFileResult =
            lambdaRecorder<File, FileInfo, String?, String?, EventId?, Result<FakeMediaUploadHandler>> { file, _, _, _, _ ->
                assertThat(file.exists()).isTrue()
                Result.success(FakeMediaUploadHandler())
            }
        val room = FakeJoinedRoom(
            liveTimeline = FakeTimeline().apply {
                sendFileLambda = sendFileResult
            },
        )
        val presenter = createAttachmentsPreviewPresenter(
            room = room,
            displayMediaQualitySelectorViews = true,
            onDoneListener = OnDoneListener {},
            mediaPreProcessor = FakeMediaPreProcessor().apply {
                givenResult(
                    Result.success(
                        MediaUploadInfo.AnyFile(
                            file = editedFile,
                            fileInfo = FileInfo(
                                mimetype = MimeTypes.Jpeg,
                                size = editedFile.length(),
                                thumbnailInfo = null,
                                thumbnailSource = null,
                            )
                        )
                    )
                )
            },
            attachmentImageEditor = FakeAttachmentImageEditor {
                Result.success(
                    EditedLocalMedia(
                        localMedia = aLocalMedia(uri = editedFile.toUri()),
                        file = editedFile,
                    )
                )
            }
        )

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()

            editorState.eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
            val appliedState = consumeItemsUntilPredicate { !it.isApplyingImageEdits && it.imageEditorState == null }.last()

            appliedState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            sendFileResult.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - image with generic mime type and png extension is still editable`() = runTest {
        val localMedia = aLocalMedia(
            uri = mockMediaUrl,
            mediaInfo = anImageMediaInfo().copy(
                mimeType = MimeTypes.OctetStream,
                filename = "Screenshot.png",
                fileExtension = "png",
            ),
        )
        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(Attachment.Media(localMedia)),
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.canEditImage).isTrue()

            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()
            assertThat(editorState.imageEditorState).isNotNull()
        }
    }

    @Test
    fun `present - image can still be edited when editor can decode it despite generic media info`() = runTest {
        val localMedia = aLocalMedia(
            uri = mockMediaUrl,
            mediaInfo = anImageMediaInfo().copy(
                mimeType = MimeTypes.OctetStream,
                filename = "",
                fileExtension = "",
            ),
        )
        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(Attachment.Media(localMedia)),
            attachmentImageEditor = FakeAttachmentImageEditor(
                canEditResult = true,
            ) {
                Result.success(
                    EditedLocalMedia(
                        localMedia = localMedia.copy(uri = Uri.parse("file:///tmp/decoded.jpeg")),
                        file = File("/tmp/decoded.jpeg"),
                    )
                )
            }
        )

        presenter.test {
            val initialState = consumeItemsUntilPredicate { it.canEditImage }.last()
            assertThat(initialState.canEditImage).isTrue()

            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()
            assertThat(editorState.imageEditorState).isNotNull()
        }
    }

    @Test
    fun `present - sendAsFile video is pre-processed with best fitting preset`() = runTest {
        val mediaPreProcessor = FakeMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            attachments = listOf(
                Attachment.Media(
                    localMedia = aLocalMedia(mockMediaUrl, aVideoMediaInfo()),
                    sendAsFile = true,
                )
            ),
            mediaPreProcessor = mediaPreProcessor,
            // Selector views are hidden in the sendAsFile flow, which triggers the auto pre-process path.
            displayMediaQualitySelectorViews = false,
            mediaOptimizationSelectorPresenterFactory = FakeMediaOptimizationSelectorPresenterFactory {
                MediaOptimizationSelectorState(
                    index = 0,
                    maxUploadSize = AsyncData.Success(250_000_000L),
                    videoSizeEstimations = AsyncData.Success(
                        persistentListOf(
                            VideoUploadEstimation(VideoCompressionPreset.HIGH, sizeInBytes = 513_216_000L, canUpload = false),
                            VideoUploadEstimation(VideoCompressionPreset.STANDARD, sizeInBytes = 228_096_000L, canUpload = true),
                            VideoUploadEstimation(VideoCompressionPreset.LOW, sizeInBytes = 57_024_000L, canUpload = true),
                        )
                    ),
                    isImageOptimizationEnabled = false,
                    selectedVideoPreset = VideoCompressionPreset.STANDARD,
                    displayMediaSelectorViews = false,
                    displayVideoPresetSelectorDialog = false,
                    eventSink = {},
                )
            },
            mediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(
                config = MediaOptimizationConfig(
                    compressImages = true,
                    videoCompressionPreset = VideoCompressionPreset.LOW,
                )
            ),
        )

        presenter.test {
            consumeItemsUntilPredicate { mediaPreProcessor.processCallCount > 0 }
            assertThat(mediaPreProcessor.lastMediaOptimizationConfig).isEqualTo(
                MediaOptimizationConfig(
                    compressImages = false,
                    videoCompressionPreset = VideoCompressionPreset.STANDARD,
                )
            )
        }
    }

    @Test
    fun `present - sending several media after image edits restarts preprocessing`() = runTest {
        val sendImageResult =
            lambdaRecorder { _: File, _: File?, _: ImageInfo, _: String?, _: String?, _: EventId?, _: String? ->
                Result.success(FakeMediaUploadHandler())
            }
        val firstLocalMedia = aLocalMedia(uri = Uri.parse("file:///tmp/original-1.jpeg"))
        val secondLocalMedia = aLocalMedia(uri = Uri.parse("file:///tmp/original-2.jpeg"))
        val editedUri = Uri.parse("file:///tmp/edited-1.jpeg")
        val onDoneListener = lambdaRecorder<Unit> { }
        val presenter = createAttachmentsPreviewPresenter(
            room = FakeJoinedRoom(
                liveTimeline = FakeTimeline().apply {
                    sendImageLambda = sendImageResult
                },
            ),
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            attachments = persistentListOf(
                aMediaAttachment(firstLocalMedia),
                aMediaAttachment(secondLocalMedia),
            ),
            displayMediaQualitySelectorViews = false,
            attachmentImageEditor = FakeAttachmentImageEditor {
                Result.success(
                    EditedLocalMedia(
                        localMedia = firstLocalMedia.copy(uri = editedUri),
                        file = File("/tmp/edited-1.jpeg"),
                    )
                )
            },
            onDoneListener = OnDoneListener { onDoneListener() },
        )

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()

            editorState.eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
            val appliedState = consumeItemsUntilPredicate { !it.isApplyingImageEdits && it.imageEditorState == null }.last()

            appliedState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            sendImageResult.assertions().isCalledExactly(2)
            onDoneListener.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - photos picked together are sent in order as one batch, the caption and reply on the first only`() = runTest {
        val sentMedia = SentMediaRecorder()
        val pendingMediaBatches = PendingMediaBatches(FakeSystemClock())
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            onDoneListener = {},
            inReplyToEventId = AN_EVENT_ID,
            pendingMediaBatches = pendingMediaBatches,
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.caption }).containsExactly(A_CAPTION, null, null).inOrder()
            assertThat(sentMedia.sent.map { it.inReplyTo }).containsExactly(AN_EVENT_ID, null, null).inOrder()
            val batches = sentMedia.sent.map { it.batch() }
            assertThat(batches.map { it?.index }).containsExactly(0, 1, 2).inOrder()
            assertThat(batches.map { it?.count }.toSet()).containsExactly(3)
            assertThat(batches.map { it?.id }.toSet()).hasSize(1)
            val pendingMedia = PendingMediaBatches.Media(filename = "image.jpg", fileSize = 1000, mimeType = MimeTypes.Jpeg)
            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t0"), pendingMedia)).isEqualTo(batches[0])
        }
    }

    @Test
    fun `present - a batch that fails part way sends only the rest on retry, in the same batch`() = runTest {
        val sentMedia = SentMediaRecorder(failingCall = 1)
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            onDoneListener = {},
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            assertThat((failedState.sendActionState as SendActionState.Failure).mediaInfos).hasSize(2)
            failedState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            val readyState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
            readyState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.caption }).containsExactly(A_CAPTION, null, null).inOrder()
            val batches = sentMedia.sent.map { it.batch() }
            assertThat(batches.map { it?.index }).containsExactly(0, 1, 2).inOrder()
            assertThat(batches.map { it?.id }.toSet()).hasSize(1)
        }
    }

    @Test
    fun `present - retrying a batch that failed part way prepares and sends only the rest, without the caption or the reply`() = runTest {
        val sentMedia = SentMediaRecorder(failingCall = 1)
        val mediaPreProcessor = PickedFileMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = {},
            inReplyToEventId = AN_EVENT_ID,
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            mediaPreProcessor.processed.clear()
            failedState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(mediaPreProcessor.processed).containsExactly("1.jpeg", "2.jpeg").inOrder()
            assertThat(sentMedia.sent.map { it.filename }).containsExactly("0.jpeg", "1.jpeg", "2.jpeg").inOrder()
            assertThat(sentMedia.sent.map { it.caption }).containsExactly(A_CAPTION, null, null).inOrder()
            assertThat(sentMedia.sent.map { it.inReplyTo }).containsExactly(AN_EVENT_ID, null, null).inOrder()
            val batches = sentMedia.sent.map { it.batch() }
            assertThat(batches.map { it?.index }).containsExactly(0, 1, 2).inOrder()
            assertThat(batches.map { it?.count }.toSet()).containsExactly(3)
            assertThat(batches.map { it?.id }.toSet()).hasSize(1)
        }
    }

    @Test
    fun `present - editing a photo after a batch failed part way sends the edited photo in its place on retry`() = runTest {
        val sentMedia = SentMediaRecorder(failingCall = 1)
        val attachments = photoLibraryAttachments(3)
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = attachments,
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            onDoneListener = {},
            attachmentImageEditor = FakeAttachmentImageEditor {
                val edited = (attachments[2] as Attachment.Media).localMedia.copy(uri = Uri.parse("file:///tmp/2-edited.jpeg"))
                Result.success(EditedLocalMedia(localMedia = edited, file = File("/tmp/2-edited.jpeg")))
            },
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            failedState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SetCurrentCarouselIndex(2))
            consumeItemsUntilPredicate { it.currentIndex == 2 }.last().eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            val editorState = consumeItemsUntilPredicate { it.imageEditorState != null }.last()
            editorState.eventSink(AttachmentsPreviewEvent.RotateImageToTheLeft)
            consumeItemsUntilPredicate { it.imageEditorState?.edits?.hasChanges == true }.last().eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
            consumeItemsUntilPredicate { it.imageEditorState == null && it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.filename }).containsExactly("0.jpeg", "1.jpeg", "2-edited.jpeg").inOrder()
            val batches = sentMedia.sent.map { it.batch() }
            assertThat(batches.map { it?.index }).containsExactly(0, 1, 2).inOrder()
            assertThat(batches.map { it?.id }.toSet()).hasSize(1)
        }
    }

    @Test
    fun `present - a batch registers each photo as it is enqueued, so one that stops part way leaves no entry for the rest`() = runTest {
        val sentMedia = SentMediaRecorder(failingCall = 1)
        val pendingMediaBatches = PendingMediaBatches(FakeSystemClock())
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            onDoneListener = {},
            pendingMediaBatches = pendingMediaBatches,
        )
        fun photo(filename: String) = PendingMediaBatches.Media(filename = filename, fileSize = 1000, mimeType = MimeTypes.Jpeg)
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()

            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t0"), photo("0.jpeg"))).isEqualTo(sentMedia.sent.single().batch())
            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo("1.jpeg"))).isNull()
            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t2"), photo("2.jpeg"))).isNull()

            failedState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }
            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t1"), photo("1.jpeg"))?.index).isEqualTo(1)
            assertThat(pendingMediaBatches.bindBatch(A_ROOM_ID, TransactionId("t2"), photo("2.jpeg"))?.index).isEqualTo(2)
        }
    }

    @Test
    fun `present - the photos not sent stay prepared after a batch fails part way, and are cleaned up once sent`() = runTest {
        val sentMedia = SentMediaRecorder(failingCall = 1)
        val mediaPreProcessor = PickedFileMediaPreProcessor()
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = {},
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            assertThat(mediaPreProcessor.cleanUpCount).isEqualTo(0)

            failedState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.filename }).containsExactly("0.jpeg", "1.jpeg", "2.jpeg").inOrder()
            assertThat(mediaPreProcessor.cleanUpCount).isEqualTo(1)
        }
    }

    @Test
    fun `present - once the first photo of a batch has gone out, the caption is cleared and locked`() = runTest {
        val presenter = createAttachmentsPreviewPresenter(
            room = SentMediaRecorder(failingCall = 1).room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            onDoneListener = {},
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.canEditCaption).isTrue()
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure && !it.canEditCaption }.last()

            assertThat(failedState.textEditorState.messageMarkdown(FakePermalinkBuilder())).isEmpty()
        }
    }

    @Test
    fun `present - the caption stays editable when the first photo of a batch fails`() = runTest {
        val presenter = createAttachmentsPreviewPresenter(
            room = SentMediaRecorder(failingCall = 0).room(),
            attachments = photoLibraryAttachments(3),
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            onDoneListener = {},
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.textEditorState.setMarkdown(A_CAPTION)
            initialState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()

            assertThat(failedState.canEditCaption).isTrue()
            assertThat(failedState.textEditorState.messageMarkdown(FakePermalinkBuilder())).isEqualTo(A_CAPTION)
        }
    }

    @Test
    fun `present - sending again while photos are still going out sends nothing twice`() = runTest {
        val firstUpload = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val timeline = FakeTimeline().apply {
            sendImageLambda = { file, _, _, _, _, _, _ ->
                sent += file.name
                Result.success(if (sent.size == 1) AwaitingMediaUploadHandler(firstUpload) else FakeMediaUploadHandler())
            }
        }
        val presenter = createAttachmentsPreviewPresenter(
            room = FakeJoinedRoom(liveTimeline = timeline),
            attachments = photoLibraryAttachments(2),
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            onDoneListener = {},
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.Uploading }.last()
                .eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.Uploading }
            firstUpload.complete(Unit)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sent).containsExactly("0.jpeg", "1.jpeg").inOrder()
        }
    }

    @Test
    fun `present - a photo edited while the others are still going out keeps its edited file until the preview is dismissed`() = runTest {
        val firstUpload = CompletableDeferred<Unit>()
        var sendCount = 0
        val timeline = FakeTimeline().apply {
            sendImageLambda = { _, _, _, _, _, _, _ ->
                when (sendCount++) {
                    0 -> Result.success(AwaitingMediaUploadHandler(firstUpload))
                    else -> Result.failure(IllegalStateException("Sending failed"))
                }
            }
        }
        val attachments = photoLibraryAttachments(2)
        val editedFile = File.createTempFile("1-edited", ".jpeg").apply { deleteOnExit() }
        val presenter = createAttachmentsPreviewPresenter(
            room = FakeJoinedRoom(liveTimeline = timeline),
            attachments = attachments,
            mediaPreProcessor = PickedFileMediaPreProcessor(),
            temporaryUriDeleter = FakeTemporaryUriDeleter {},
            onDoneListener = {},
            attachmentImageEditor = FakeAttachmentImageEditor {
                val edited = (attachments[1] as Attachment.Media).localMedia.copy(uri = Uri.fromFile(editedFile))
                Result.success(EditedLocalMedia(localMedia = edited, file = editedFile))
            },
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.Uploading }.last()
                .eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SetCurrentCarouselIndex(1))
            consumeItemsUntilPredicate { it.currentIndex == 1 }.last().eventSink(AttachmentsPreviewEvent.OpenImageEditor)
            consumeItemsUntilPredicate { it.imageEditorState != null }.last().eventSink(AttachmentsPreviewEvent.RotateImageToTheLeft)
            consumeItemsUntilPredicate { it.imageEditorState?.edits?.hasChanges == true }.last().eventSink(AttachmentsPreviewEvent.ApplyImageEdits)
            consumeItemsUntilPredicate { it.imageEditorState == null && it.sendActionState is SendActionState.Sending.ReadyToUpload }.last()
                .eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState is SendActionState.Sending.Uploading }
            firstUpload.complete(Unit)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            assertThat(editedFile.exists()).isTrue()

            failedState.eventSink(AttachmentsPreviewEvent.CancelAndDismiss)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }
            assertThat(editedFile.exists()).isFalse()
        }
    }

    @Test
    fun `present - dismissing a failure to prepare the photos prepares them again on the next send`() = runTest {
        val sentMedia = SentMediaRecorder()
        val mediaPreProcessor = FakeMediaPreProcessor().apply { givenResult(Result.failure(IllegalStateException("Preparing failed"))) }
        val onDoneListener = lambdaRecorder<Unit> { }
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(2),
            mediaPreProcessor = mediaPreProcessor,
            onDoneListener = { onDoneListener() },
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            val failedState = consumeItemsUntilPredicate { it.sendActionState is SendActionState.Failure }.last()
            mediaPreProcessor.givenImageResult()
            failedState.eventSink(AttachmentsPreviewEvent.CancelAndClearSendState)
            val clearedState = awaitLastSequentialItem()
            assertThat(clearedState.sendActionState).isEqualTo(SendActionState.Idle)
            clearedState.eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent).hasSize(2)
            onDoneListener.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - photos are sent without a batch when the flag is off`() = runTest {
        val sentMedia = SentMediaRecorder()
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(2),
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            onDoneListener = {},
            featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.GuaMediaStacks.key to false)),
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.extraContent }).containsExactly(null, null)
        }
    }

    @Test
    fun `present - media not picked from the photo library are sent without a batch`() = runTest {
        val sentMedia = SentMediaRecorder()
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = List(2) { index -> aMediaAttachment(aLocalMedia(uri = Uri.parse("file:///tmp/$index.jpeg"))) },
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            onDoneListener = {},
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            assertThat(sentMedia.sent.map { it.extraContent }).containsExactly(null, null)
        }
    }

    @Test
    fun `present - a pick larger than the limit is sent as several batches, a lone last item without one`() = runTest {
        val sentMedia = SentMediaRecorder()
        val presenter = createAttachmentsPreviewPresenter(
            room = sentMedia.room(),
            attachments = photoLibraryAttachments(MediaBatch.MAX_SEND_COUNT + 1),
            mediaPreProcessor = FakeMediaPreProcessor().apply { givenImageResult() },
            onDoneListener = {},
        )
        presenter.test {
            awaitItem().eventSink(AttachmentsPreviewEvent.SendAttachment)
            consumeItemsUntilPredicate { it.sendActionState == SendActionState.Done }

            val batches = sentMedia.sent.map { it.batch() }
            assertThat(batches.take(MediaBatch.MAX_SEND_COUNT).map { it?.count }.toSet()).containsExactly(MediaBatch.MAX_SEND_COUNT)
            assertThat(batches.last()).isNull()
        }
    }

    private fun photoLibraryAttachments(count: Int) = List(count) { index ->
        aMediaAttachment(aLocalMedia(uri = Uri.parse("file:///tmp/$index.jpeg")), isFromPhotoLibrary = true)
    }

    private data class SentMedia(val caption: String?, val inReplyTo: EventId?, val extraContent: String?, val filename: String = "") {
        fun batch(): MediaBatch? = extraContent?.let { extra ->
            MediaBatch.fromContent(JsonObject(Json.parseToJsonElement(extra).jsonObject + ("msgtype" to JsonPrimitive("m.image"))))
        }
    }

    // Records each photo sent, in order; the send numbered [failingCall], counting from 0, fails.
    private class SentMediaRecorder(private val failingCall: Int? = null) {
        val sent = mutableListOf<SentMedia>()
        private var calls = 0

        fun room(): FakeJoinedRoom {
            val timeline = FakeTimeline()
            timeline.sendImageLambda = { file, _, _, caption, _, inReplyTo, extraContent ->
                if (calls++ == failingCall) {
                    Result.failure(IllegalStateException("Sending failed"))
                } else {
                    sent += SentMedia(caption, inReplyTo, extraContent, file.name)
                    Result.success(FakeMediaUploadHandler())
                }
            }
            return FakeJoinedRoom(liveTimeline = timeline)
        }
    }

    private class AwaitingMediaUploadHandler(private val done: CompletableDeferred<Unit>) : MediaUploadHandler {
        override suspend fun await(): Result<Unit> {
            done.await()
            return Result.success(Unit)
        }

        override fun cancel() = Unit
    }

    // Prepares each picked file as an image of the same name, so a test can tell which picked file each send carries.
    private class PickedFileMediaPreProcessor : MediaPreProcessor {
        val processed = mutableListOf<String>()
        var cleanUpCount = 0
            private set

        override suspend fun process(
            uri: Uri,
            mimeType: String,
            deleteOriginal: Boolean,
            mediaOptimizationConfig: MediaOptimizationConfig,
        ): Result<MediaUploadInfo> {
            val filename = uri.lastPathSegment.orEmpty()
            processed += filename
            return Result.success(aMediaUploadInfo(filePath = filename))
        }

        override fun cleanUp() {
            cleanUpCount++
        }
    }

    private fun TestScope.createAttachmentsPreviewPresenter(
        attachments: List<Attachment> = listOf(
            aMediaAttachment(
                aLocalMedia(
                    uri = mockMediaUrl,
                )
            ),
        ),
        room: JoinedRoom = FakeJoinedRoom(),
        timelineMode: Timeline.Mode = Timeline.Mode.Live,
        permalinkBuilder: PermalinkBuilder = FakePermalinkBuilder(),
        mediaPreProcessor: MediaPreProcessor = FakeMediaPreProcessor(),
        temporaryUriDeleter: TemporaryUriDeleter = FakeTemporaryUriDeleter(),
        onDoneListener: OnDoneListener = OnDoneListener { lambdaError() },
        displayMediaQualitySelectorViews: Boolean = false,
        mediaOptimizationSelectorPresenterFactory: FakeMediaOptimizationSelectorPresenterFactory = FakeMediaOptimizationSelectorPresenterFactory(
            fakePresenter = {
                MediaOptimizationSelectorState(
                    index = 0,
                    maxUploadSize = AsyncData.Uninitialized,
                    videoSizeEstimations = AsyncData.Uninitialized,
                    isImageOptimizationEnabled = null,
                    selectedVideoPreset = null,
                    displayMediaSelectorViews = displayMediaQualitySelectorViews,
                    displayVideoPresetSelectorDialog = false,
                    eventSink = {},
                )
            }
        ),
        mediaOptimizationConfigProvider: FakeMediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(),
        attachmentImageEditor: AttachmentImageEditor = FakeAttachmentImageEditor {
            val localMediaResult = (attachments.first() as? Attachment.Media)?.localMedia?.copy(uri = Uri.parse("file:///tmp/default-edited.jpeg"))
            if (localMediaResult != null) {
                Result.success(
                    EditedLocalMedia(
                        localMedia = localMediaResult,
                        file = File("/tmp/default-edited.jpeg"),
                    )
                )
            } else {
                Result.failure(IllegalStateException("Check test values"))
            }
        },
        videoCompressionPresetSelector: VideoCompressionPresetSelector = VideoCompressionPresetSelector(),
        inReplyToEventId: EventId? = null,
        featureFlagService: FakeFeatureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.GuaMediaStacks.key to true)),
        pendingMediaBatches: PendingMediaBatches = PendingMediaBatches(FakeSystemClock()),
    ): AttachmentsPreviewPresenter {
        return AttachmentsPreviewPresenter(
            attachments = attachments.toImmutableList(),
            onDoneListener = onDoneListener,
            mediaSenderFactory = MediaSenderFactory { timelineMode ->
                DefaultMediaSender(
                    preProcessor = mediaPreProcessor,
                    room = room,
                    timelineMode = timelineMode,
                    mediaOptimizationConfigProvider = {
                        MediaOptimizationConfig(compressImages = true, videoCompressionPreset = VideoCompressionPreset.STANDARD)
                    }
                )
            },
            permalinkBuilder = permalinkBuilder,
            temporaryUriDeleter = temporaryUriDeleter,
            attachmentImageEditor = attachmentImageEditor,
            sessionCoroutineScope = this,
            dispatchers = testCoroutineDispatchers(),
            mediaOptimizationSelectorPresenterFactory = mediaOptimizationSelectorPresenterFactory,
            videoCompressionPresetSelector = videoCompressionPresetSelector,
            timelineMode = timelineMode,
            inReplyToEventId = inReplyToEventId,
            mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
            room = room,
            featureFlagService = featureFlagService,
            pendingMediaBatches = pendingMediaBatches,
        )
    }

    private class FakeAttachmentImageEditor(
        private val canEditResult: Boolean = true,
        private val result: () -> Result<EditedLocalMedia>,
    ) : AttachmentImageEditor {
        override suspend fun canEdit(localMedia: LocalMedia): Boolean {
            return canEditResult
        }

        override suspend fun exportEdits(
            localMedia: LocalMedia,
            edits: AttachmentImageEdits,
        ): Result<EditedLocalMedia> {
            return result()
        }
    }

    private val mediaUploadInfo = MediaUploadInfo.AnyFile(
        File("test"),
        FileInfo(
            mimetype = MimeTypes.Any,
            size = 999L,
            thumbnailInfo = null,
            thumbnailSource = null,
        )
    )
}
