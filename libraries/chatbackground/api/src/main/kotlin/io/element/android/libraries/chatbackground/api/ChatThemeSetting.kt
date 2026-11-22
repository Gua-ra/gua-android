/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbackground.api

import kotlinx.coroutines.flow.StateFlow

interface ChatThemeSetting {
    /** The stock look until the cached or stored default has loaded. */
    val theme: StateFlow<ChatTheme>

    fun preload()

    /** Shows [theme] straight away; a failed write puts the stored default back. */
    suspend fun set(theme: ChatTheme): Result<Unit>
}
