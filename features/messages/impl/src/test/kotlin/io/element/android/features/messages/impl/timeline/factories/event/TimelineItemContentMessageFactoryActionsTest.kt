/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.messagemenu.FakeMentionSpanUpdater
import io.element.android.features.messages.impl.messagemenu.selectableText
import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.features.messages.impl.timeline.actions.displaysBody
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemNoticeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.utils.messagesummary.DefaultMessageSummaryFormatter
import io.element.android.features.messages.test.timeline.FakeHtmlConverterProvider
import io.element.android.features.messages.test.timeline.FakeTextPillificator
import io.element.android.libraries.androidutils.filesize.FakeFileSizeFormatter
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.htmlrenderer.api.DocumentNode
import io.element.android.libraries.htmlrenderer.test.FakeHtmlMessageParser
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.LatestJsonProvider
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import io.element.android.libraries.matrix.api.timeline.item.event.MessageType
import io.element.android.libraries.matrix.api.timeline.item.event.NoticeMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.permalink.FakePermalinkParser
import io.element.android.libraries.matrix.test.timeline.aProfileDetails
import io.element.android.libraries.mediaviewer.test.util.FileExtensionExtractorWithoutValidation
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class TimelineItemContentMessageFactoryActionsTest : RobolectricTest() {
    private val action = MessageAction(label = AN_ACTION_LABEL, url = AN_ACTION_URL)

    @Test
    fun `a trailing link repeated by an action is hidden from the display only`() = runTest {
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = anEventJsonProvider(),
        ) as TimelineItemTextContent
        assertThat(result.actions).containsExactly(action)
        assertThat(result.plainText).isEqualTo("Statement\nTotal: 10")
        assertThat(result.formattedBody.toString()).doesNotContain(AN_ACTION_URL)
        assertThat(result.body).isEqualTo(A_BODY)
        assertThat(result.htmlBody).contains(AN_ACTION_URL)
        assertThat(result.selectableText(FakeMentionSpanUpdater())).contains(AN_ACTION_LABEL)
    }

    @Test
    fun `a notice hides the trailing link repeated by an action`() = runTest {
        val result = create(
            type = NoticeMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = anEventJsonProvider(msgType = "m.notice"),
        ) as TimelineItemNoticeContent
        assertThat(result.actions).containsExactly(action)
        assertThat(result.plainText).isEqualTo("Statement\nTotal: 10")
        assertThat(result.htmlBody).contains(AN_ACTION_URL)
    }

    @Test
    fun `a body that is only the fallback link has no text left to display`() = runTest {
        val html = "<a href=\"$AN_ACTION_URL\">$AN_ACTION_LABEL</a>"
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, html)),
            latestJsonProvider = anEventJsonProvider(html = html),
        ) as TimelineItemTextContent
        assertThat(result.actions).containsExactly(action)
        assertThat(result.plainText).isEmpty()
        assertThat(result.displaysBody).isFalse()
        assertThat(result.htmlBody).contains(AN_ACTION_URL)
    }

    @Test
    fun `read-only previews keep the hidden fallback link`() = runTest {
        val formatter = DefaultMessageSummaryFormatter(RuntimeEnvironment.getApplication())
        val linkOnlyHtml = "<a href=\"$AN_ACTION_URL\">$AN_ACTION_LABEL</a>"
        val linkOnly = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, linkOnlyHtml)),
            latestJsonProvider = anEventJsonProvider(html = linkOnlyHtml),
        ) as TimelineItemTextContent
        val notice = create(
            type = NoticeMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = anEventJsonProvider(msgType = "m.notice"),
        ) as TimelineItemNoticeContent
        assertThat(linkOnly.plainText).isEmpty()
        assertThat(formatter.format(linkOnly)).isEqualTo(AN_ACTION_LABEL)
        assertThat(notice.plainText).isEqualTo("Statement\nTotal: 10")
        assertThat(formatter.format(notice)).isEqualTo("Statement\nTotal: 10\n$AN_ACTION_LABEL")
    }

    @Test
    fun `rule 1 judges the HTML as sent rather than the displayed one`() = runTest {
        // Only the HTML as sent has the reply fallback, which rule 1 rejects.
        val sentHtml = "<mx-reply><blockquote>In reply to</blockquote></mx-reply>$A_HTML_WITH_TRAILING_LINK"
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = anEventJsonProvider(html = sentHtml),
        ) as TimelineItemTextContent
        assertThat(result.actions).isEmpty()
        assertThat(result.plainText).isEqualTo("Statement\nTotal: 10\n$AN_ACTION_LABEL")
        assertThat(result.formattedBody.toString()).contains(AN_ACTION_URL)
    }

    @Test
    fun `a body the sanitisers left empty shows the actions alone`() = runTest {
        listOf("", "<span></span>").forEach { sanitisedHtml ->
            val result = create(
                type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, sanitisedHtml)),
                latestJsonProvider = anEventJsonProvider(),
            ) as TimelineItemTextContent
            assertThat(result.htmlDocument).isNull()
            assertThat(result.actions).containsExactly(action)
            assertThat(result.displaysBody).isFalse()
        }
    }

    @Test
    fun `a message without actions keeps its trailing link`() = runTest {
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = LatestJsonProvider { """{"type":"m.room.message","content":{"msgtype":"m.text","body":"body"}}""" },
        ) as TimelineItemTextContent
        assertThat(result.actions).isEmpty()
        assertThat(result.plainText).isEqualTo("Statement\nTotal: 10\n$AN_ACTION_LABEL")
        assertThat(result.sourceHtmlBody).isNull()
        assertThat(result.htmlBody).contains(AN_ACTION_URL)
    }

    @Test
    fun `an action whose url is not a link of the message is dropped and the message is unchanged`() = runTest {
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = anEventJsonProvider(url = "https://bills.example.org/other"),
        ) as TimelineItemTextContent
        assertThat(result.actions).isEmpty()
        assertThat(result.plainText).isEqualTo("Statement\nTotal: 10\n$AN_ACTION_LABEL")
        assertThat(result.htmlBody).contains(AN_ACTION_URL)
    }

    @Test
    fun `a link in the middle of the message keeps showing next to its action`() = runTest {
        val html = "Open <a href=\"$AN_ACTION_URL\">$AN_ACTION_LABEL</a> today."
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, html)),
            latestJsonProvider = anEventJsonProvider(html = html),
        ) as TimelineItemTextContent
        assertThat(result.actions).containsExactly(action)
        assertThat(result.plainText).isEqualTo("Open $AN_ACTION_LABEL today.")
        assertThat(result.sourceHtmlBody).isNull()
    }

    @Test
    fun `a message created without the event JSON has no actions`() = runTest {
        val result = create(
            type = TextMessageType(body = A_BODY, formatted = FormattedBody(MessageFormat.HTML, A_HTML_WITH_TRAILING_LINK)),
            latestJsonProvider = null,
        ) as TimelineItemTextContent
        assertThat(result.actions).isEmpty()
        assertThat(result.formattedBody.toString()).contains(AN_ACTION_URL)
    }

    private suspend fun create(type: MessageType, latestJsonProvider: LatestJsonProvider?): TimelineItemEventContent {
        return createTimelineItemContentMessageFactory().create(
            content = MessageContent(body = A_BODY, inReplyTo = null, isEdited = false, threadInfo = null, type = type),
            senderId = A_USER_ID,
            senderProfile = aProfileDetails(),
            eventId = AN_EVENT_ID,
            latestJsonProvider = latestJsonProvider,
        )
    }

    private fun anEventJsonProvider(
        msgType: String = "m.text",
        html: String = A_HTML_WITH_TRAILING_LINK,
        url: String = AN_ACTION_URL,
    ): LatestJsonProvider {
        val json = buildJsonObject {
            put("type", "m.room.message")
            putJsonObject("content") {
                put("msgtype", msgType)
                put("body", A_BODY)
                put("format", "org.matrix.custom.html")
                put("formatted_body", html)
                putJsonArray("global.gua.actions") {
                    addJsonObject {
                        put("type", "open_url")
                        put("label", AN_ACTION_LABEL)
                        put("url", url)
                    }
                }
            }
        }.toString()
        return LatestJsonProvider { json }
    }

    private fun createTimelineItemContentMessageFactory() = TimelineItemContentMessageFactory(
        fileSizeFormatter = FakeFileSizeFormatter(),
        fileExtensionExtractor = FileExtensionExtractorWithoutValidation(),
        htmlConverterProvider = FakeHtmlConverterProvider(transformDom = { it.body().html() }),
        permalinkParser = FakePermalinkParser(result = { PermalinkData.FallbackLink(Uri.parse(it)) }),
        textPillificator = FakeTextPillificator(),
        htmlMessageParser = FakeHtmlMessageParser(parseResult = { DocumentNode(persistentListOf()) }),
        featureFlagService = FakeFeatureFlagService(),
    )

    private companion object {
        const val A_BODY = "Statement\nTotal: 10\nView statement: https://bills.example.org/statement/2026-08"
        const val AN_ACTION_URL = "https://bills.example.org/statement/2026-08"
        const val AN_ACTION_LABEL = "View statement"
        const val A_HTML_WITH_TRAILING_LINK = "<strong>Statement</strong><br>Total: 10<br><a href=\"$AN_ACTION_URL\">$AN_ACTION_LABEL</a>"
    }
}
