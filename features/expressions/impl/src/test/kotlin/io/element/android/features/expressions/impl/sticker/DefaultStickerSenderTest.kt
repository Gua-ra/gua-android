/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl.sticker

import com.google.common.truth.Truth.assertThat
import io.element.android.features.expressions.api.parseJsonObject
import io.element.android.features.expressions.api.sticker.StickerFile
import io.element.android.features.expressions.api.sticker.StickerImage
import io.element.android.features.expressions.api.sticker.StickerRelation
import io.element.android.features.expressions.api.sticker.StickerSendException
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultStickerSenderTest {
    private val cat = StickerImage(
        url = "mxc://gua.global/cat",
        body = "Cat",
        info = parseJsonObject("""{"w":256,"h":256,"mimetype":"image/webp","size":120034}"""),
    )

    @Test
    fun `an image is sent as an m-sticker event with its info`() = runTest {
        val sends = mutableListOf<Pair<String, String>>()
        val sender = createSender(sendRawResult = { type, content ->
            sends += type to content
            Result.success(AN_EVENT_ID)
        })

        val result = sender.send(cat, StickerRelation.None)

        assertThat(result.getOrNull()).isEqualTo(AN_EVENT_ID)
        assertThat(sends.single().first).isEqualTo("m.sticker")
        assertThat(sends.single().second)
            .isEqualTo("""{"body":"Cat","info":{"h":256,"mimetype":"image/webp","size":120034,"w":256},"url":"mxc://gua.global/cat"}""")
    }

    @Test
    fun `a reply and a thread add their relation`() = runTest {
        val contents = mutableListOf<String>()
        val sender = createSender(sendRawResult = { _, content ->
            contents += content
            Result.success(AN_EVENT_ID)
        })

        sender.send(cat, StickerRelation.Reply(AN_EVENT_ID))
        sender.send(cat, StickerRelation.Thread(rootId = A_THREAD_ID, latestEventId = AN_EVENT_ID_2))

        assertThat(parseJsonObject(contents[0])["m.relates_to"]).isEqualTo(parseJsonObject("""{"m.in_reply_to":{"event_id":"${AN_EVENT_ID.value}"}}"""))
        assertThat(parseJsonObject(contents[1])["m.relates_to"]).isEqualTo(
            parseJsonObject(
                """{"event_id":"${A_THREAD_ID.value}","is_falling_back":true,"m.in_reply_to":{"event_id":"${AN_EVENT_ID_2.value}"},"rel_type":"m.thread"}"""
            )
        )
    }

    @Test
    fun `a failed send is a send failure`() = runTest {
        val sender = createSender(sendRawResult = { _, _ -> Result.failure(Exception("offline")) })

        val result = sender.send(cat, StickerRelation.None)

        assertThat(result.exceptionOrNull()).isInstanceOf(StickerSendException.SendFailed::class.java)
    }

    @Test
    fun `an upload returns the image at its display size`() = runTest {
        val matrixClient = FakeMatrixClient().apply { givenUploadMediaResult(Result.success("mxc://gua.global/new")) }
        val sender = createSender(matrixClient = matrixClient)

        val image = sender.upload(aStickerFile()).getOrThrow()

        assertThat(image).isEqualTo(
            StickerImage(
                url = "mxc://gua.global/new",
                body = "Hi",
                info = parseJsonObject("""{"h":128,"mimetype":"image/webp","size":3,"w":256}"""),
            )
        )
    }

    @Test
    fun `a failed upload is an upload failure`() = runTest {
        val matrixClient = FakeMatrixClient().apply { givenUploadMediaResult(Result.failure(Exception("offline"))) }
        val sender = createSender(matrixClient = matrixClient)

        val result = sender.upload(aStickerFile())

        assertThat(result.exceptionOrNull()).isInstanceOf(StickerSendException.UploadFailed::class.java)
    }

    private fun aStickerFile() = StickerFile(
        bytes = byteArrayOf(1, 2, 3),
        mimetype = "image/webp",
        width = 512,
        height = 256,
        body = "Hi",
        isAnimated = false,
    )

    private fun createSender(
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        sendRawResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(AN_EVENT_ID) },
    ) = DefaultStickerSender(
        matrixClient = matrixClient,
        room = FakeJoinedRoom(sendRawResult = sendRawResult),
    )
}
