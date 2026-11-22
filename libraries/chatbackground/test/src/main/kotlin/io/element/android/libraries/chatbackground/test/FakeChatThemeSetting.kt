/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.test

import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.ChatThemeSetting
import kotlinx.coroutines.flow.MutableStateFlow

class FakeChatThemeSetting(
    initialTheme: ChatTheme = ChatTheme.STOCK,
    private val setResult: (ChatTheme) -> Result<Unit> = { Result.success(Unit) },
) : ChatThemeSetting {
    override val theme = MutableStateFlow(initialTheme)
    val sentThemes = mutableListOf<ChatTheme>()

    override fun preload() = Unit

    override suspend fun set(theme: ChatTheme): Result<Unit> {
        sentThemes += theme
        return setResult(theme).onSuccess { this.theme.value = theme }
    }
}
