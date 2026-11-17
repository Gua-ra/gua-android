/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.actions

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.LatestJsonProvider
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import io.element.android.libraries.matrix.test.permalink.FakePermalinkParser
import io.element.android.libraries.matrix.ui.messages.toHtmlDocument
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jsoup.nodes.Document
import org.junit.Test

class MessageActionsTest : RobolectricTest() {
    private val permalinkParser = FakePermalinkParser(result = { PermalinkData.FallbackLink(Uri.parse(it)) })

    @Test
    fun `the shared fixtures give the expected actions and fallback`() {
        val fixtures = readFixtures()
        assertThat(fixtures).hasSize(FIXTURE_COUNT)
        val mismatches = fixtures.mapNotNull { fixture ->
            val formatted = displayedFormattedBody(fixture.content)
            val document = formatted?.toHtmlDocument(permalinkParser)
            val htmlBefore = document?.body()?.html()
            val eventJson = buildJsonObject {
                put("type", "m.room.message")
                put("content", fixture.content)
            }.toString()
            val result = MessageActions.extract(formatted, document, LatestJsonProvider { eventJson })
            val hidden = document?.body()?.html() != htmlBefore
            "${fixture.name}: actions ${result.actions}, fallback hidden $hidden".takeIf {
                result.actions != fixture.actions || hidden != fixture.fallbackHidden || hidden != (result.sourceDocument != null)
            }
        }
        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `the event JSON is not read when the message has no HTML body`() {
        listOf(null, FormattedBody(format = MessageFormat.UNKNOWN, body = A_HTML)).forEach { formatted ->
            val result = MessageActions.extract(formatted, null, LatestJsonProvider { error("The event JSON must not be read") })
            assertThat(result.actions).isEmpty()
        }
    }

    @Test
    fun `the actions are kept when the sanitised document lost the link or is missing`() {
        listOf(htmlDocument("Total: 10<br>"), null).forEach { document ->
            val result = MessageActions.extract(anHtmlBody(A_HTML), document, anEventJsonProvider(html = A_HTML))
            assertThat(result.actions).containsExactly(AN_ACTION)
            assertThat(result.sourceDocument).isNull()
        }
    }

    @Test
    fun `missing or malformed event JSON gives no actions`() {
        listOf(null, "{", "[]", "{\"content\": 1}", "{\"content\": {\"global.gua.actions\": 1}}").forEach { eventJson ->
            val document = htmlDocument(A_HTML)
            val result = MessageActions.extract(anHtmlBody(A_HTML), document, LatestJsonProvider { eventJson })
            assertThat(result.actions).isEmpty()
            assertThat(document.body().html()).contains(URL)
        }
    }

    @Test
    fun `the url grammar is checked on the raw string`() {
        val valid = listOf(
            "https://example.org",
            "https://a.b-c.example.org:443/x?q=1#frag",
            "https://example.org:65535",
            "https://EXAMPLE.org/A~b_c.d-e",
            "https://example.org?q=a%2Fb",
            "https://example.org#",
            "https://xn--caf-dma.example.org/",
            "https://example.org/" + "a".repeat(2048 - "https://example.org/".length),
        )
        val invalid = listOf(
            "https://",
            "https://example.org.",
            "https://example..org",
            "https://-example.org",
            "https://example.org:",
            "https://example.org:0",
            "https://example.org:08443",
            "https://example.org:123456",
            "https://[::1]/",
            "https://café.example.org/",
            "https://example.org/a b",
            "https://example.org/a%zz",
            "https://example.org/a\"b",
            "https://example.org/<x>",
            "https://example.org/x\n",
            " https://example.org",
            "https:/example.org",
            "Https://example.org",
            "https://example.org/" + "a".repeat(2049 - "https://example.org/".length),
        )
        assertThat(valid.filterNot(MessageActionUrl::isValid)).isEmpty()
        assertThat(invalid.filter(MessageActionUrl::isValid)).isEmpty()
    }

    @Test
    fun `hiding the fallback also removes the line breaks and spaces around it`() {
        val document = extractWithAction("<strong>Statement</strong><br>Total: 10 <br> <a href=\"$URL\">$LABEL</a><br>")
        assertThat(document.body().html()).isEqualTo("<strong>Statement</strong><br>Total: 10")
    }

    @Test
    fun `hiding the fallback trims the text and no-break spaces before it`() {
        val document = extractWithAction("Total: 10&nbsp;<br> <a href=\"$URL\">$LABEL</a>")
        assertThat(document.body().html()).isEqualTo("Total: 10")
    }

    @Test
    fun `hiding the fallback removes the paragraph it leaves empty`() {
        val document = extractWithAction("<p>Statement</p><p><a href=\"$URL\">$LABEL</a></p>")
        assertThat(document.body().html()).isEqualTo("<p>Statement</p>")
    }

    @Test
    fun `a trailing link inside a sent div is hidden although the sanitiser drops the div`() {
        val document = extractWithAction("Total: 10<div><span><a href=\"$URL\">$LABEL</a></span></div>")
        assertThat(document.body().html()).isEqualTo("Total: 10")
    }

    @Test
    fun `a trailing link inside other formatting is kept`() {
        listOf(
            "Total: 10<br><strong><a href=\"$URL\">$LABEL</a></strong>",
            "Total: 10<br><em><a href=\"$URL\">$LABEL</a></em>",
        ).forEach { html ->
            assertThat(extractWithAction(html).body().html()).contains(URL)
        }
    }

    @Test
    fun `a link followed by an empty container in the sent HTML is kept`() {
        listOf("<p></p>", "<div></div>", "<span></span>").forEach { suffix ->
            val html = A_HTML + suffix
            // The displayed document ends with the link, as if the sanitiser had dropped what follows it.
            val document = htmlDocument(A_HTML)
            val result = MessageActions.extract(anHtmlBody(html), document, anEventJsonProvider(html = html))
            assertWithMessage(suffix).that(result.actions).containsExactly(AN_ACTION)
            assertWithMessage(suffix).that(document.body().html()).contains(URL)
        }
    }

    @Test
    fun `a trailing link inside every kind of container is hidden`() {
        val document = extractWithAction("Total: 10<div><div><div><p><span><span><span>$A_LINK</span></span></span></p></div></div></div>")
        assertThat(document.body().html()).doesNotContain(URL)
    }

    @Test
    fun `a link the displayed document shows differently is hidden by its href`() {
        val mentionUrl = "https://matrix.to/#/@support:example.org"
        val html = "Hello<br><a href=\"$mentionUrl\">Contact support</a>"
        val mentionParser = FakePermalinkParser(result = { PermalinkData.UserLink(UserId("@support:example.org")) })
        val document = htmlDocument(html, mentionParser)
        assertThat(document.body().html()).contains("@Contact support")
        val result = MessageActions.extract(anHtmlBody(html), document, anEventJsonProvider(html = html, label = "Contact support", url = mentionUrl))
        assertThat(result.actions).containsExactly(MessageAction(label = "Contact support", url = mentionUrl))
        assertThat(document.body().html()).isEqualTo("Hello")
        assertThat(result.sourceDocument?.body()?.html()).contains(mentionUrl)
    }

    @Test
    fun `a displayed trailing link with another href is kept`() {
        val document = htmlDocument("Total: 10<br><a href=\"$OTHER_URL\">$LABEL</a>")
        val result = MessageActions.extract(anHtmlBody(A_HTML), document, anEventJsonProvider(html = A_HTML))
        assertThat(result.actions).containsExactly(AN_ACTION)
        assertThat(result.sourceDocument).isNull()
        assertThat(document.body().html()).contains(OTHER_URL)
    }

    @Test
    fun `rule 1 rejects control characters other than TAB, LF, FF and CR`() {
        val kept = listOf('\t', '\n', '\u000C', '\r', '\u00A0', '\u2028')
        val rejected = listOf('\u0000', '\u0008', '\u000B', '\u000E', '\u001F', '\u007F', '\u0080', '\u0085', '\u009F')
        (kept + rejected).forEach { char ->
            val html = "Total:${char}10<br>$A_LINK"
            assertWithMessage("U+${char.code.toString(16)}").that(extract(html).isNotEmpty()).isEqualTo(char in kept)
        }
    }

    @Test
    fun `rule 1 allows only the listed character references`() {
        val kept = listOf(
            "&amp;",
            "&lt;",
            "&gt;",
            "&quot;",
            "&apos;",
            "&nbsp;",
            "&#32;",
            "&#126;",
            "&#160;",
            "&#0000065;",
            "&#X41;",
            "&#x000041;",
            "&#xD7FF;",
            "&#xE000;",
            "&#x10FFFD;",
            "&#1114109;",
        )
        val rejected = listOf(
            "&AMP;",
            "&Amp;",
            "&amp",
            "&times;",
            "&copy",
            "& ",
            "&#;",
            "&#x;",
            "&#0;",
            "&#9;",
            "&#10;",
            "&#31;",
            "&#127;",
            "&#x80;",
            "&#159;",
            "&#xD800;",
            "&#xDFFF;",
            "&#x110000;",
            "&#1114112;",
            "&#00000065;",
            "&#x0000041;",
            "&#\uFF16\uFF15;",
            "&#x\uFF21;",
            "&#65",
            "&#x41",
        )
        (kept + rejected).forEach { reference ->
            val html = "Total: 10 $reference<br>$A_LINK"
            assertWithMessage(reference).that(extract(html).isNotEmpty()).isEqualTo(reference in kept)
        }
    }

    @Test
    fun `rule 1 rejects noncharacters written as is or as a reference`() {
        val kept = listOf(0xFDCF, 0xFDF0, 0xFFFD, 0x10FFFD)
        val rejected = listOf(0xFDD0, 0xFDEF, 0xFFFE, 0xFFFF, 0x1FFFE, 0x1FFFF, 0x10FFFE, 0x10FFFF)
        (kept + rejected).forEach { codePoint ->
            listOf(String(Character.toChars(codePoint)), "&#$codePoint;", "&#x${codePoint.toString(16)};").forEach { char ->
                listOf(
                    "${char}Total: 10<br>$A_LINK",
                    "<b>${char}Total</b><br>$A_LINK",
                    "&amp;${char}Total: 10<br>$A_LINK",
                    "Total:${char}10<br>$A_LINK",
                    "Total: 10<br><a href=\"$OTHER_URL?$char\">Other</a> $A_LINK",
                    "$A_HTML$char",
                ).forEach { html ->
                    val message = "U+${codePoint.toString(16).uppercase()} in $html"
                    assertWithMessage(message).that(extract(html).isNotEmpty()).isEqualTo(codePoint in kept)
                }
            }
        }
    }

    @Test
    fun `rule 1 decodes the references of an href before it is compared`() {
        val url = "https://bills.example.org/s?a=1&b=2"
        listOf("&amp;", "&#38;", "&#x26;", "&#X26;", "&#0038;").forEach { ampersand ->
            val html = "Total: 10<br><a href=\"https://bills.example.org/s?a=1${ampersand}b=2\">$LABEL</a>"
            assertWithMessage(html).that(extract(html, url = url)).containsExactly(MessageAction(label = LABEL, url = url))
        }
        listOf("&", "&amp", "&AMP;", "&times;", "&#0;", "&#xD800;").forEach { ampersand ->
            val html = "Total: 10<br><a href=\"https://bills.example.org/s?a=1${ampersand}b=2\">Other</a> $A_LINK"
            assertWithMessage(html).that(extract(html)).isEmpty()
        }
    }

    @Test
    fun `rule 1 keeps every allowed spelling of a tag`() {
        listOf(
            "Total: 10<br><a href='$URL'>$LABEL</a>",
            "Total: 10<br><a href=\"https://bills.example.org/a'b\">Other</a> $A_LINK",
            "Total: 10<br><a\thref=\"$URL\">$LABEL</a>",
            "Total: 10<br><a\nhref=\"$URL\">$LABEL</a>",
            "Total: 10<br><a\u000Chref=\"$URL\">$LABEL</a>",
            "Total: 10<br><a\rhref=\"$URL\">$LABEL</a>",
            "Total: 10<br><a  href=\"$URL\">$LABEL</a>",
            "Total: 10<br><a \r\n\t href=\"$URL\">$LABEL</a>",
            "Total<br>10<br/>Due<br />$A_LINK",
            "<a>Statement</a> <a href=\"\">Total</a><br>$A_LINK",
            "Total &gt; 5 > 4 \"quoted\" 'single' a=b c/d<br>$A_LINK",
        ).forEach { html ->
            assertWithMessage(html).that(extract(html)).containsExactly(AN_ACTION)
        }
    }

    @Test
    fun `rule 1 keeps every allowed element nested as written`() {
        val html = "<blockquote><h1>A</h1><h2>B</h2><h3>C</h3><h4>D</h4><h5>E</h5><h6>F</h6>" +
            "<div><p><b>a</b> <i>b</i> <em>c</em> <strong>d</strong> <u>e</u> <s>f</s> <code>g</code> <del>h</del> <sub>i</sub> <sup>j</sup> " +
            "<span>k</span></p><ul><li>l<ol><li><p>m</p></li></ol></li></ul></div></blockquote>" +
            "<h1><b>Total</b></h1><b><p>10</p></b><a href=\"$URL\"><p>$LABEL</p></a>"
        assertThat(extract(html)).containsExactly(AN_ACTION)
    }

    @Test
    fun `rule 1 rejects every other spelling of a tag`() {
        listOf(
            "$A_HTML<!-- sent by a bot -->",
            "$A_HTML<![CDATA[ ]]>",
            "$A_HTML<?BR>",
            "$A_HTML<?",
            "<!DOCTYPE html>$A_HTML",
            "<STRONG>Total</STRONG><br>$A_LINK",
            "<strong>Total</STRONG><br>$A_LINK",
            "Total: 10<BR>$A_LINK",
            "Total: 10<br><A href=\"$URL\">$LABEL</a>",
            "Total: 10<br><a HREF=\"$URL\">$LABEL</a>",
            "Total: 10<br><a href=$URL>$LABEL</a>",
            "Total: 10<br><a href = \"$URL\">$LABEL</a>",
            "Total: 10<br><a href =\"$URL\">$LABEL</a>",
            "Total: 10<br><a href= \"$URL\">$LABEL</a>",
            "Total: 10<br><a href=\"$URL\" href=\"$OTHER_URL\">$LABEL</a>",
            "Total: 10<br><a href=\"$URL\" title=\"Statement\">$LABEL</a>",
            "Total: 10<br><a title=\"Statement\" href=\"$URL\">$LABEL</a>",
            "Total: 10<br><a href=\"$URL\" >$LABEL</a>",
            "Total: <a title=\"Statement\">10</a><br>$A_LINK",
            "Total: 10<br><a\u00A0href=\"$URL\">Other</a> $A_LINK",
            "<strong class=\"x\">Total</strong><br>$A_LINK",
            "<span data-msc4286-external-payment-details>Total</span><br>$A_LINK",
            "Total: 10<br><a href=\"$URL?x=>\">Other</a> $A_LINK",
            "Total: 10<br><a href=\"$URL?x=<\">Other</a> $A_LINK",
            "Total: 10<br><a href='$URL'b'>Other</a> $A_LINK",
            "<strong/>Total</strong><br>$A_LINK",
            "<span / >Total</span><br>$A_LINK",
            "<strong >Total</strong><br>$A_LINK",
            "<strong>Total</strong ><br>$A_LINK",
            "Total: 10<br  />$A_LINK",
            "Total: 10<br/ >$A_LINK",
            "$A_HTML</br>",
            "<table></table>$A_HTML",
            "<h7>Total</h7>$A_HTML",
            "<bloc\u212Aquote>Total</bloc\u212Aquote>$A_HTML",
            "Total < 20<br>$A_LINK",
            "$A_HTML <",
            "$A_HTML </",
            "$A_HTML <a href=\"$URL\"",
            "$A_HTML <a href=\"$URL",
        ).forEach { html ->
            assertWithMessage(html).that(extract(html)).isEmpty()
        }
    }

    @Test
    fun `rule 1 rejects elements that do not nest as written`() {
        listOf(
            "<b>Total <i>10</b></i><br>$A_LINK",
            "<b>Total: 10<br>$A_LINK",
            "Total: 10</b><br>$A_LINK",
            "<b>Total: 10</i></b><br>$A_LINK",
        ).forEach { html ->
            assertWithMessage(html).that(extract(html)).isEmpty()
        }
    }

    @Test
    fun `rule 1 allows 200 start tags, br included`() {
        assertThat(extract("Total" + "<br>".repeat(199) + A_LINK)).containsExactly(AN_ACTION)
        assertThat(extract("Total" + "<br>".repeat(200) + A_LINK)).isEmpty()
    }

    @Test
    fun `rule 1 allows 20 open elements`() {
        val names = listOf("div", "blockquote", "span", "b", "i", "em", "strong").flatMap { listOf(it, it, it) }
        mapOf(20 to true, 21 to false).forEach { (depth, kept) ->
            val wrappers = names.take(depth)
            val html = wrappers.joinToString("") { "<$it>" } + "Total" + wrappers.reversed().joinToString("") { "</$it>" } + A_HTML
            assertWithMessage("$depth").that(extract(html).isNotEmpty()).isEqualTo(kept)
        }
    }

    @Test
    fun `rule 1 allows 3 open elements with the same name`() {
        mapOf(
            "<b><i><b><i><b>Total</b></i></b></i></b><br>$A_LINK" to true,
            "<b>Total</b><b>:</b><b> 10</b><b>.</b><br>$A_LINK" to true,
            "<b><i><b><i><b><i><b>Total</b></i></b></i></b></i></b><br>$A_LINK" to false,
        ).forEach { (html, kept) ->
            assertWithMessage(html).that(extract(html).isNotEmpty()).isEqualTo(kept)
        }
    }

    @Test
    fun `rule 1 keeps paragraphs free of blocks, lists and headings`() {
        assertThat(extract("<div><p>Total</p></div><p><span><b>10</b></span></p><ul><li><p>Due</p></li></ul><br>$A_LINK")).containsExactly(AN_ACTION)
        (listOf("blockquote", "div", "ol", "p", "ul") + HEADINGS).forEach { name ->
            listOf("<p>Total<$name>10</$name></p>$A_LINK", "<p><b>Total<$name>10</$name></b></p>$A_LINK").forEach { html ->
                assertWithMessage(html).that(extract(html)).isEmpty()
            }
        }
    }

    @Test
    fun `rule 1 keeps each list item directly inside a list`() {
        mapOf(
            "<ul><li>Total</li></ul><ol><li><b>10</b></li></ol><br>$A_LINK" to true,
            "<li>Total</li>$A_LINK" to false,
            "<div><li>Total</li></div>$A_LINK" to false,
            "<ul><li><b><li>Total</li></b></li></ul>$A_LINK" to false,
            "<ul><li>Total<li>10</li></li></ul>$A_LINK" to false,
        ).forEach { (html, kept) ->
            assertWithMessage(html).that(extract(html).isNotEmpty()).isEqualTo(kept)
        }
    }

    @Test
    fun `rule 1 allows only list items and HTML spaces written as is directly inside a list`() {
        val kept = listOf("\t", "\n", "\u000C", "\r", " ", "\r\n").flatMap { space ->
            listOf("ul", "ol").map { list -> "<$list>$space<li>Total</li>$space<li>10</li>$space</$list>" }
        } + listOf(
            "<ul><li>Total<ol><li>10</li></ol></li></ul>",
            "<ol><li>Total <b>10</b><ul>\n<li>Due</li>\n</ul></li></ol>",
        )
        val children = listOf("Total", "\u00A0", "&#32;", "&nbsp;", "<br>", A_LINK, "<b>10</b>", "<p>10</p>", "<ul><li>10</li></ul>", "<ol><li>10</li></ol>")
        val rejected = children.flatMap { child ->
            listOf("ul", "ol").flatMap { list -> listOf("<$list>$child<li>Total</li></$list>", "<$list><li>Total</li>$child</$list>") }
        }
        (kept.map { it to true } + rejected.map { it to false }).forEach { (list, isKept) ->
            val html = "$list<br>$A_LINK"
            assertWithMessage(html).that(extract(html).isNotEmpty()).isEqualTo(isKept)
        }
    }

    @Test
    fun `rule 1 keeps headings and links out of their own kind`() {
        assertThat(extract("<h1><b>Total</b></h1><div><h2>10</h2></div><a href=\"$URL\"><b>$LABEL</b></a>")).containsExactly(AN_ACTION)
        val nested = HEADINGS.flatMap { name -> listOf("<h1>Total <$name>10</$name></h1>$A_LINK", "<$name>Total <b><h1>10</h1></b></$name>$A_LINK") } +
            "<a href=\"$OTHER_URL\">Total <a href=\"$URL\">$LABEL</a></a>" +
            "<a>Total <b><a href=\"$URL\">$LABEL</a></b></a>"
        nested.forEach { html ->
            assertWithMessage(html).that(extract(html)).isEmpty()
        }
    }

    @Test
    fun `the white space set is the one the spec lists`() {
        val expected = ('\u0009'..'\u000D') + ' ' + '\u0085' + '\u00A0' + '\u1680' + ('\u2000'..'\u200A') +
            '\u2028' + '\u2029' + '\u202F' + '\u205F' + '\u3000'
        val actual = (Char.MIN_VALUE..Char.MAX_VALUE).filter { it.toString().isWhiteSpaceOnly() }
        assertThat(actual).containsExactlyElementsIn(expected)
    }

    @Test
    fun `the source document keeps the hidden link`() {
        val document = htmlDocument(A_HTML)
        val result = MessageActions.extract(anHtmlBody(A_HTML), document, anEventJsonProvider(html = A_HTML))
        assertThat(document.body().html()).doesNotContain(URL)
        assertThat(result.sourceDocument?.body()?.html()).isEqualTo(htmlDocument(A_HTML).body().html())
    }

    // The displayed document is null when jsoup reads nothing of the HTML, as for a literal U+FFFF at its start.
    private fun extract(html: String, url: String = URL): List<MessageAction> =
        MessageActions.extract(anHtmlBody(html), anHtmlBody(html).toHtmlDocument(permalinkParser), anEventJsonProvider(html = html, url = url)).actions

    private fun extractWithAction(html: String): Document {
        val document = htmlDocument(html)
        val result = MessageActions.extract(anHtmlBody(html), document, anEventJsonProvider(html = html))
        assertThat(result.actions).containsExactly(AN_ACTION)
        return document
    }

    private fun anEventJsonProvider(html: String, label: String = LABEL, url: String = URL): LatestJsonProvider {
        val json = buildJsonObject {
            put("type", "m.room.message")
            putJsonObject("content") {
                put("msgtype", "m.text")
                put("body", "x")
                put("format", "org.matrix.custom.html")
                put("formatted_body", html)
                putJsonArray("global.gua.actions") {
                    addJsonObject {
                        put("type", "open_url")
                        put("label", label)
                        put("url", url)
                    }
                }
            }
        }.toString()
        return LatestJsonProvider { json }
    }

    private fun displayedFormattedBody(content: JsonObject): FormattedBody? {
        val isEdit = (content["m.relates_to"] as? JsonObject)?.get("rel_type") == JsonPrimitive("m.replace")
        val displayed = (if (isEdit) content["m.new_content"] else content) as? JsonObject ?: return null
        val formattedBody = (displayed["formatted_body"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val format = if (displayed["format"] == JsonPrimitive("org.matrix.custom.html")) MessageFormat.HTML else MessageFormat.UNKNOWN
        return FormattedBody(format = format, body = formattedBody)
    }

    private fun anHtmlBody(html: String) = FormattedBody(format = MessageFormat.HTML, body = html)

    private fun htmlDocument(html: String, parser: PermalinkParser = permalinkParser): Document {
        val document = checkNotNull(FormattedBody(format = MessageFormat.HTML, body = html).toHtmlDocument(parser))
        return document.apply { outputSettings().prettyPrint(false) }
    }

    private fun readFixtures(): List<Fixture> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURES_RESOURCE)) {
            "$FIXTURES_RESOURCE is missing from the test resources"
        }
        val cases = Json.parseToJsonElement(stream.reader().use { it.readText() }) as? JsonArray
        assertWithMessage("$FIXTURES_RESOURCE must be a non-empty JSON array").that(cases.orEmpty()).isNotEmpty()
        val decoded = checkNotNull(cases).mapIndexed { index, case -> index to runCatchingExceptions { case.toFixture() } }
        val failures = decoded.mapNotNull { (index, fixture) -> fixture.exceptionOrNull()?.let { "case $index: ${it.message}" } }
        assertWithMessage("Fixture cases that cannot be decoded").that(failures).isEmpty()
        return decoded.map { (_, fixture) -> fixture.getOrThrow() }
    }

    private fun JsonElement.toFixture(): Fixture {
        val case = this as? JsonObject ?: error("not an object")
        val expect = case["expect"] as? JsonObject ?: error("expect is not an object")
        val actions = expect["actions"] as? JsonArray ?: error("expect.actions is not an array")
        return Fixture(
            name = case.requireString("name"),
            content = case["content"] as? JsonObject ?: error("content is not an object"),
            actions = actions.map { action ->
                val fields = action as? JsonObject ?: error("an expected action is not an object")
                MessageAction(label = fields.requireString("label"), url = fields.requireString("url"))
            },
            fallbackHidden = (expect["fallback_hidden"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
                ?: error("expect.fallback_hidden is not a boolean"),
        )
    }

    private fun JsonObject.requireString(key: String): String {
        return (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("$key is not a string")
    }

    private data class Fixture(
        val name: String,
        val content: JsonObject,
        val actions: List<MessageAction>,
        val fallbackHidden: Boolean,
    )

    private companion object {
        const val FIXTURES_RESOURCE = "message-actions-fixtures.json"
        const val FIXTURE_COUNT = 128
        const val URL = "https://bills.example.org/statement/2026-08"
        const val OTHER_URL = "https://bills.example.org/other"
        const val LABEL = "View statement"
        val AN_ACTION = MessageAction(label = LABEL, url = URL)
        val HEADINGS = listOf("h1", "h2", "h3", "h4", "h5", "h6")
        const val A_LINK = "<a href=\"$URL\">$LABEL</a>"
        const val A_HTML = "Total: 10<br>$A_LINK"
    }
}
