/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.actions

// The Unicode White_Space set as the message actions spec lists it, so every platform trims the same characters.
private val WHITE_SPACE: Set<Char> = buildSet {
    addAll('\u0009'..'\u000D')
    addAll(listOf(' ', '\u0085', '\u00A0', '\u1680'))
    addAll('\u2000'..'\u200A')
    addAll(listOf('\u2028', '\u2029', '\u202F', '\u205F', '\u3000'))
}

internal fun String.trimWhiteSpace(): String = trim { it in WHITE_SPACE }

internal fun String.trimEndWhiteSpace(): String = trimEnd { it in WHITE_SPACE }

internal fun String.isWhiteSpaceOnly(): Boolean = all { it in WHITE_SPACE }
