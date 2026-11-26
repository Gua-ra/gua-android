/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import io.element.android.features.messages.impl.timeline.actions.MessageAction
import io.element.android.libraries.htmlrenderer.api.DocumentNode
import io.element.android.libraries.matrix.ui.messages.toPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jsoup.nodes.Document

data class TimelineItemNoticeContent(
    override val body: String,
    override val htmlDocument: Document?,
    override val formattedBody: CharSequence,
    override val isEdited: Boolean,
    override val messageTree: DocumentNode? = null,
    // GUA FORK: htmlDocument, formattedBody and plainText no longer show the fallback link of these actions; the source values still have it.
    val actions: ImmutableList<MessageAction> = persistentListOf(),
    val sourceHtmlBody: String? = null,
    val sourceFormattedBody: CharSequence? = null,
    val sourcePlainText: String? = null,
) : TimelineItemTextBasedContent {
    override val type: String = "TimelineItemNoticeContent"
    override val plainText: String = htmlDocument?.toPlainText() ?: body

    // GUA FORK: an edit starts from the HTML from before the fallback link was hidden.
    override val htmlBody: String?
        get() = sourceHtmlBody ?: htmlDocument?.body()?.html()
}
