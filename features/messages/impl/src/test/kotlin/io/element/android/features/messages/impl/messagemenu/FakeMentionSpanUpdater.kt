/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.runtime.Composable
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.MentionSpanFormatter
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.libraries.textcomposer.mentions.getMentionSpans

class FakeMentionSpanUpdater(
    private val displayNames: Map<UserId, String> = emptyMap(),
) : MentionSpanUpdater {
    private val formatter = object : MentionSpanFormatter {
        override fun formatDisplayText(mentionType: MentionType): CharSequence {
            return when (mentionType) {
                is MentionType.User -> displayNames[mentionType.userId] ?: mentionType.userId.value
                else -> ""
            }
        }
    }

    override fun updateMentionSpans(text: CharSequence): CharSequence {
        text.getMentionSpans().forEach { it.updateDisplayText(formatter) }
        return text
    }

    @Composable
    override fun rememberMentionSpans(text: CharSequence): CharSequence = updateMentionSpans(text)
}
