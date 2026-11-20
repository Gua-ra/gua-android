/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.actions

import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemNoticeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.LatestJsonProvider
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class MessageAction(
    val label: String,
    val url: String,
)

/**
 * The kept actions of a message.
 * [sourceDocument] is a copy of the document from before its fallback link was hidden, or null when nothing was hidden.
 */
data class ExtractedMessageActions(
    val actions: ImmutableList<MessageAction> = persistentListOf(),
    val sourceDocument: Document? = null,
)

object MessageActions {
    private const val CONTENT_KEY = "global.gua.actions"
    private const val HTML_FORMAT = "org.matrix.custom.html"
    private const val OPEN_URL_TYPE = "open_url"
    private const val MAX_ACTIONS = 3
    private const val MAX_LABEL_LENGTH = 40
    private const val MAX_FORMATTED_BODY_LENGTH = 4096
    private const val MAX_START_TAGS = 200
    private const val MAX_OPEN_ELEMENTS = 20
    private const val MAX_OPEN_WITH_SAME_NAME = 3
    private const val HTML_SPACE = "\t\n\u000C\r "
    private const val HREF = "href="
    private val ALLOWED_CONTROLS = setOf('\t'.code, '\n'.code, '\u000C'.code, '\r'.code)
    private val MESSAGE_TYPES = setOf("m.text", "m.notice")
    private val CONTAINER_TAGS = setOf("p", "div", "span")

    // The names of the "<name>" and "</name>" forms; br has forms of its own and no end tag.
    private val ELEMENT_NAMES = setOf(
        "a",
        "b",
        "blockquote",
        "code",
        "del",
        "div",
        "em",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "i",
        "li",
        "ol",
        "p",
        "s",
        "span",
        "strong",
        "sub",
        "sup",
        "u",
        "ul",
    )
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val NOT_IN_PARAGRAPH = HEADINGS + setOf("blockquote", "div", "li", "ol", "p", "ul")
    private val LIST_PARENTS = setOf("ul", "ol")
    private val BREAK_TAG_ENDS = listOf(">", "/>", " />")
    private val NAMED_REFERENCES = listOf("&amp;", "&lt;", "&gt;", "&quot;", "&apos;", "&nbsp;")
    private val SURROGATES = Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code

    /**
     * [document] is the displayed document of [formatted], null when the sanitisers left nothing of it.
     * Also hides the fallback link in [document], so nothing that is displayed may be derived from [document] before this runs.
     */
    fun extract(formatted: FormattedBody?, document: Document?, latestJsonProvider: LatestJsonProvider?): ExtractedMessageActions {
        // Rule 1 and the links are judged on formatted_body as sent, which only the event JSON is sure to hold unchanged.
        if (formatted?.format != MessageFormat.HTML || latestJsonProvider == null) return ExtractedMessageActions()
        val parsed = fromEventJson(latestJsonProvider())
        val sourceDocument = parsed.fallback?.let { fallback -> document?.let { hideFallbackLink(it, fallback) } }
        return ExtractedMessageActions(actions = parsed.actions, sourceDocument = sourceDocument)
    }

    private fun fromEventJson(eventJson: String?): ParsedActions {
        if (eventJson == null) return ParsedActions()
        val content = runCatchingExceptions { (Json.parseToJsonElement(eventJson) as? JsonObject)?.get("content") as? JsonObject }.getOrNull()
        return content?.let(::fromContent) ?: ParsedActions()
    }

    private fun fromContent(content: JsonObject): ParsedActions {
        // An edit is displayed from m.new_content, so its actions are read from there too.
        val displayed = (if (content.isReplacement()) content["m.new_content"] else content) as? JsonObject
        val formattedBody = displayed
            ?.takeIf { it.string("msgtype") in MESSAGE_TYPES && it.string("format") == HTML_FORMAT }
            ?.string("formatted_body")
            ?.takeIf(::isStrictForm)
        val candidates = (displayed?.get(CONTENT_KEY) as? JsonArray).orEmpty().take(MAX_ACTIONS).mapNotNull { it.toAction() }
        if (formattedBody == null || candidates.isEmpty()) return ParsedActions()
        // The links and the fallback are judged on the HTML as sent, before the sanitisers drop or unwrap any tag.
        // jsoup does not preprocess its input, so line breaks are normalised here as the WHATWG input stream does.
        val sentHtml = formattedBody.replace("\r\n", "\n").replace('\r', '\n')
        // Rule 1 admits only markup that every HTML parser, jsoup included, builds into the tree its tags describe.
        val sentBody = Jsoup.parseBodyFragment(sentHtml).body()
        val hrefs = sentBody.select("a[href]").mapTo(HashSet()) { it.attr("href") }
        val actions = candidates.filter { it.url in hrefs }.distinctBy { it.url }.toImmutableList()
        val trailingLink = sentBody.trailingLink()
        return ParsedActions(
            actions = actions,
            fallback = trailingLink?.let { link -> actions.firstOrNull { link.isLinkTo(it) } },
        )
    }

    /** Returns a copy of [document] from before the link to [fallback] was hidden, or null when the document does not end with that link. */
    private fun hideFallbackLink(document: Document, fallback: MessageAction): Document? {
        val body = document.body()
        // The sanitisers may have reshaped the sent HTML, so only a link to the same href at the end of the displayed document is hidden.
        // Its text was checked on the sent HTML; the displayed text can differ, as a mention gains an @.
        val link = body.trailingLink()?.takeIf { it.attr("href") == fallback.url } ?: return null
        val sourceDocument = document.clone()
        var node: Node? = link
        while (node != null) {
            val parent = node.parent()
            node.removeIgnorableSiblings()
            node.remove()
            // A container left with only line breaks and spaces would still take a line, so it goes too.
            node = parent?.takeIf { it != body && it.childNodes().all { child -> child.isIgnorable() } }
        }
        return sourceDocument
    }

    // Rule 1: one pass over the raw string with the stack of open element names, in code points for the length.
    private fun isStrictForm(html: String): Boolean {
        if (html.codePointCount(0, html.length) > MAX_FORMATTED_BODY_LENGTH || html.codePoints().anyMatch { it.isRejectedScalar() }) return false
        val open = ArrayList<String>()
        var startTags = 0
        var index = 0
        while (index < html.length) {
            // Directly inside a ul or ol only li elements and HTML spaces written as is may appear, as some renderers drop anything else.
            val inList = open.lastOrNull() in LIST_PARENTS
            index = when {
                html[index] == '&' -> if (inList) -1 else html.referenceEnd(index)
                html[index] != '<' -> if (inList && html[index] !in HTML_SPACE) -1 else index + 1
                html.startsWith("</", index) -> html.endTagEnd(index, open)
                else -> if (++startTags > MAX_START_TAGS) -1 else html.startTagEnd(index, open, inList)
            }
            if (index < 0) return false
        }
        return open.isEmpty()
    }

    // jsoup reads a literal U+FFFF as the end of its input, so noncharacters are rejected along with the controls.
    private fun Int.isRejectedScalar(): Boolean = Character.isISOControl(this) && this !in ALLOWED_CONTROLS || isNoncharacter()

    // U+FDD0 to U+FDEF and the last two code points of every plane.
    private fun Int.isNoncharacter(): Boolean = this in 0xFDD0..0xFDEF || this % 0x10000 >= 0xFFFE

    // Returns the index after the reference at [start], or -1 when it is not one that rule 1 allows.
    private fun String.referenceEnd(start: Int): Int {
        NAMED_REFERENCES.firstOrNull { startsWith(it, start) }?.let { return start + it.length }
        if (!startsWith("&#", start)) return -1
        val isHex = getOrNull(start + 2) == 'x' || getOrNull(start + 2) == 'X'
        val (radix, maxDigits) = if (isHex) 16 to 6 else 10 to 7
        val digitsStart = if (isHex) start + 3 else start + 2
        var end = digitsStart
        while (end < length && end - digitsStart < maxDigits && this[end].isAsciiDigit(radix)) end++
        if (end == digitsStart || getOrNull(end) != ';') return -1
        val value = substring(digitsStart, end).toInt(radix)
        val isAllowed = value <= Character.MAX_CODE_POINT && value !in SURROGATES && !Character.isISOControl(value) && !value.isNoncharacter()
        return if (isAllowed) end + 1 else -1
    }

    // digitToIntOrNull alone also takes non-ASCII digits, such as the fullwidth ones.
    private fun Char.isAsciiDigit(radix: Int): Boolean = this < '\u0080' && digitToIntOrNull(radix) != null

    // Returns the index after the start tag at [start], or -1 when rule 1 does not allow it there.
    private fun String.startTagEnd(start: Int, open: MutableList<String>, inList: Boolean): Int {
        var end = nameEnd(start + 1)
        val name = substring(start + 1, end)
        if (inList && name != "li") return -1
        if (name == "br") return BREAK_TAG_ENDS.firstOrNull { startsWith(it, end) }?.let { end + it.length } ?: -1
        if (name !in ELEMENT_NAMES) return -1
        if (name == "a" && getOrNull(end)?.let { it in HTML_SPACE } == true) end = hrefEnd(end)
        if (end < 0 || getOrNull(end) != '>' || !open.mayOpen(name)) return -1
        open.add(name)
        return end + 1
    }

    // br is never open, so "</br>" fails here like any end tag that does not close the innermost open element.
    private fun String.endTagEnd(start: Int, open: MutableList<String>): Int {
        val end = nameEnd(start + 2)
        return if (getOrNull(end) == '>' && open.removeLastOrNull() == substring(start + 2, end)) end + 1 else -1
    }

    // Names are compared as written, so an upper-case letter ends one and the tag then fails.
    private fun String.nameEnd(start: Int): Int {
        var end = start
        while (end < length && (this[end] in 'a'..'z' || this[end] in '0'..'9')) end++
        return end
    }

    // Returns the index after the quoted value of the only attribute allowed, or -1 when it is not written as rule 1 requires.
    private fun String.hrefEnd(start: Int): Int {
        var end = start
        while (end < length && this[end] in HTML_SPACE) end++
        if (!startsWith(HREF, end)) return -1
        end += HREF.length
        val quote = getOrNull(end)?.takeIf { it == '"' || it == '\'' } ?: return -1
        end++
        while (end < length && this[end] != quote) {
            end = when (this[end]) {
                '<', '>' -> return -1
                '&' -> referenceEnd(end).takeIf { it >= 0 } ?: return -1
                else -> end + 1
            }
        }
        return if (end < length) end + 1 else -1
    }

    // The other nesting rules of rule 1 for a start tag of [name], with this list as the stack of open element names.
    private fun List<String>.mayOpen(name: String): Boolean = when {
        size >= MAX_OPEN_ELEMENTS -> false
        count { it == name } >= MAX_OPEN_WITH_SAME_NAME -> false
        name == "a" && "a" in this -> false
        name in HEADINGS && any { it in HEADINGS } -> false
        name in NOT_IN_PARAGRAPH && "p" in this -> false
        name == "li" -> lastOrNull() in LIST_PARENTS
        else -> true
    }

    private fun JsonElement.toAction(): MessageAction? {
        val action = this as? JsonObject ?: return null
        if (action.string("type") != OPEN_URL_TYPE) return null
        val label = action.string("label")?.trimWhiteSpace() ?: return null
        if (label.codePointCount(0, label.length) !in 1..MAX_LABEL_LENGTH) return null
        val url = action.string("url")?.takeIf(MessageActionUrl::isValid) ?: return null
        return MessageAction(label = label, url = url)
    }

    private fun Element.trailingLink(): Element? {
        var last = lastContent()
        while (last != null && last.normalName() in CONTAINER_TAGS) last = last.lastContent()
        return last?.takeIf { it.normalName() == "a" }
    }

    // The last child other than line breaks and White_Space text, when that child is an element.
    private fun Element.lastContent(): Element? = childNodes().lastOrNull { !it.isIgnorable() } as? Element

    private fun Element.isLinkTo(action: MessageAction): Boolean = attr("href") == action.url && textContent().trimWhiteSpace() == action.label

    // As WHATWG textContent, where <br> adds nothing; jsoup's wholeText() reads it as a line feed.
    private fun Element.textContent(): String = buildString { forEachNode { node -> if (node is TextNode) append(node.wholeText) } }

    private fun Node.removeIgnorableSiblings() {
        generateSequence { nextSibling()?.takeIf { it.isIgnorable() } }.forEach { it.remove() }
        generateSequence { previousSibling()?.takeIf { it.isIgnorable() } }.forEach { it.remove() }
        (previousSibling() as? TextNode)?.let { it.text(it.wholeText.trimEndWhiteSpace()) }
    }

    private fun Node.isIgnorable(): Boolean = this is TextNode && wholeText.isWhiteSpaceOnly() || this is Element && normalName() == "br"

    private fun JsonObject.isReplacement(): Boolean = (this["m.relates_to"] as? JsonObject)?.string("rel_type") == "m.replace"

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private data class ParsedActions(
        val actions: ImmutableList<MessageAction> = persistentListOf(),
        // The kept action whose link the sent HTML ends with.
        val fallback: MessageAction? = null,
    )
}

val TimelineItemEventContent.messageActions: ImmutableList<MessageAction>
    get() = when (this) {
        is TimelineItemTextContent -> actions
        is TimelineItemNoticeContent -> actions
        else -> persistentListOf()
    }

/** The formatted body with a hidden fallback link still in it, for uses that are not display, such as text selection. */
val TimelineItemTextBasedContent.formattedBodyWithFallback: CharSequence
    get() = when (this) {
        is TimelineItemTextContent -> sourceFormattedBody
        is TimelineItemNoticeContent -> sourceFormattedBody
        else -> null
    } ?: formattedBody

/** The plain text with a hidden fallback link still in it, for read-only previews such as the action sheet header. */
val TimelineItemTextBasedContent.plainTextWithFallback: String
    get() = when (this) {
        is TimelineItemTextContent -> sourcePlainText
        is TimelineItemNoticeContent -> sourcePlainText
        else -> null
    } ?: plainText

/** False when only the buttons are displayed: the body was only the fallback link, or the sanitisers left nothing of it. */
val TimelineItemTextBasedContent.displaysBody: Boolean
    get() = messageActions.isEmpty() || htmlDocument != null && !plainText.isWhiteSpaceOnly()
