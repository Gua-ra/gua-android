/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val VARIATION_SELECTOR_16 = "\uFE0F"

object MessageQuickReactions {
    // Reaction keys are compared byte for byte across clients, so these code points must match Gua iOS.
    val defaults: ImmutableList<String> = persistentListOf(
        "\u2764\uFE0F",
        "\uD83D\uDC4D\uFE0F",
        "\uD83D\uDC4E\uFE0F",
        "\uD83D\uDE02",
        "\u203C\uFE0F",
        "\u2753\uFE0F",
    )

    const val MAX_COUNT = 10

    fun build(frequentlyUsed: List<String>): ImmutableList<String> {
        return (defaults + frequentlyUsed)
            // The same emoji can come back from the recent list with or without U+FE0F.
            .distinctBy { it.replace(VARIATION_SELECTOR_16, "") }
            .take(MAX_COUNT)
            .toImmutableList()
    }
}
