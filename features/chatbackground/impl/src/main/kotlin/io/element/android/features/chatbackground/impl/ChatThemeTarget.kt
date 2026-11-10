/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.libraries.chatbackground.api.ChatBackgroundService
import io.element.android.libraries.chatbackground.api.ChatTheme
import io.element.android.libraries.chatbackground.api.ChatThemeSetting
import io.element.android.libraries.chatbackground.api.isChatPhotoHidden
import io.element.android.libraries.chatbackground.api.isPrivateChat
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.matrix.api.media.MediaPreviewService
import io.element.android.libraries.matrix.api.media.isPreviewEnabled
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.canChangeChatBackground
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.ui.chatbackground.collectThemeAsState

enum class ChatThemeMode {
    Chat,
    Default,
}

data class ChatThemeTargetState(
    val current: ChatTheme,
    val defaultTheme: ChatTheme,
    val hasPermission: Boolean?,
    val isPhotoHidden: Boolean,
)

interface ChatThemeTarget {
    val mode: ChatThemeMode

    @Composable
    fun present(): ChatThemeTargetState

    suspend fun set(theme: ChatTheme): Result<Unit>
}

@Inject
class RoomChatThemeTarget(
    private val room: JoinedRoom,
    private val chatBackgroundService: ChatBackgroundService,
    private val chatThemeSetting: ChatThemeSetting,
    private val mediaPreviewService: MediaPreviewService,
) : ChatThemeTarget {
    override val mode = ChatThemeMode.Chat

    @Composable
    override fun present(): ChatThemeTargetState {
        val current by chatBackgroundService.collectThemeAsState(room)
        val defaultTheme by chatThemeSetting.theme.collectAsState()
        val hasPermission by room.permissionsAsState<Boolean?>(null) { it.canChangeChatBackground() }
        val mediaPreviewValue by remember { mediaPreviewService.mediaPreviewConfigFlow.mapState { it.mediaPreviewValue } }.collectAsState()
        val joinRule by remember { room.roomInfoFlow.mapState { it.joinRule } }.collectAsState()
        return ChatThemeTargetState(
            current = current,
            defaultTheme = defaultTheme,
            hasPermission = hasPermission,
            isPhotoHidden = isChatPhotoHidden(joinRule.isPrivateChat(), mediaPreviewValue.isPreviewEnabled(joinRule)),
        )
    }

    override suspend fun set(theme: ChatTheme): Result<Unit> = chatBackgroundService.set(room, theme)
}

@Inject
class PersonalDefaultChatThemeTarget(
    private val chatThemeSetting: ChatThemeSetting,
) : ChatThemeTarget {
    override val mode = ChatThemeMode.Default

    @Composable
    override fun present(): ChatThemeTargetState {
        val current by chatThemeSetting.theme.collectAsState()
        // The user's own photo always shows.
        return ChatThemeTargetState(current = current, defaultTheme = current, hasPermission = true, isPhotoHidden = false)
    }

    override suspend fun set(theme: ChatTheme): Result<Unit> = chatThemeSetting.set(theme)
}
