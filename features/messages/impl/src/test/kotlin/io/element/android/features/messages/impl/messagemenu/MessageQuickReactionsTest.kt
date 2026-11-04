/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MessageQuickReactionsTest {
    @Test
    fun `the quick reactions start with the Gua set, with the exact code points`() {
        val codePoints = MessageQuickReactions.build(emptyList()).map { emoji -> emoji.codePoints().toArray().toList() }

        assertThat(codePoints).containsExactly(
            listOf(0x2764, 0xFE0F),
            listOf(0x1F44D, 0xFE0F),
            listOf(0x1F44E, 0xFE0F),
            listOf(0x1F602),
            listOf(0x203C, 0xFE0F),
            listOf(0x2753, 0xFE0F),
        ).inOrder()
    }

    @Test
    fun `frequently used emoji follow without duplicates, up to ten in all`() {
        val reactions = MessageQuickReactions.build(listOf(FIRE, HEART, PRAY, SURPRISED, CRYING, PARTY, PARTYING))

        assertThat(reactions).containsExactlyElementsIn(MessageQuickReactions.defaults + listOf(FIRE, PRAY, SURPRISED, CRYING)).inOrder()
    }

    @Test
    fun `a frequently used emoji without the variation selector is not offered twice`() {
        val reactions = MessageQuickReactions.build(listOf(THUMBS_UP_WITHOUT_SELECTOR, HEART_WITHOUT_SELECTOR, FIRE))

        assertThat(reactions).containsExactlyElementsIn(MessageQuickReactions.defaults + FIRE).inOrder()
    }
}

private const val FIRE = "\uD83D\uDD25"
private const val HEART = "\u2764\uFE0F"
private const val HEART_WITHOUT_SELECTOR = "\u2764"
private const val THUMBS_UP_WITHOUT_SELECTOR = "\uD83D\uDC4D"
private const val PRAY = "\uD83D\uDE4F"
private const val SURPRISED = "\uD83D\uDE2E"
private const val CRYING = "\uD83D\uDE22"
private const val PARTY = "\uD83C\uDF89"
private const val PARTYING = "\uD83E\uDD73"
