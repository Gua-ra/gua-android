/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.invite.api.acceptdecline.AcceptDeclineInviteState
import io.element.android.features.leaveroom.api.LeaveRoomState
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.push.api.battery.BatteryOptimizationState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class RoomListState(
    val contextMenu: ContextMenu,
    val declineInviteMenu: DeclineInviteMenu,
    val leaveRoomState: LeaveRoomState,
    val filtersState: RoomListFiltersState,
    val searchState: RoomListSearchState,
    val spaceFiltersState: SpaceFiltersState,
    val contentState: RoomListContentState,
    val acceptDeclineInviteState: AcceptDeclineInviteState,
    val hideInvitesAvatars: Boolean,
    val canReportRoom: Boolean,
    val eventSink: (RoomListEvent) -> Unit,
) {
    val displayFilters = contentState is RoomListContentState.Rooms

    sealed interface ContextMenu {
        data object Hidden : ContextMenu
        data class Shown(
            val roomId: RoomId,
            val roomName: String?,
            val isDm: Boolean,
            val isFavorite: Boolean,
            val hasNewContent: Boolean,
            val displayClearRoomCacheAction: Boolean,
        ) : ContextMenu
    }

    sealed interface DeclineInviteMenu {
        data object Hidden : DeclineInviteMenu
        data class Shown(val roomSummary: RoomListRoomSummary) : DeclineInviteMenu
    }
}

enum class SecurityBannerState {
    None,
    SetUpRecovery,
    RecoveryKeyConfirmation,
}

@Immutable
sealed interface RoomListContentState {
    /**
     * GUA FORK: set once the repair has established only a reset can finish this device.
     *
     * Declared here, not just on the states that carry it, so the navigation effect can be written
     * once against the interface. It used to be duplicated into each state's own composable, and
     * RoomsView -- the branch every account with chats takes -- navigated without ever sending
     * EncryptionResetNavigated back. The flag latched true, its LaunchedEffect key never changed
     * again, and from then on every tap of Finish setup did nothing at all.
     */
    val encryptionSetupNeedsReset: Boolean

    data class Skeleton(val count: Int) : RoomListContentState {
        override val encryptionSetupNeedsReset = false
    }

    data class Empty(
        val securityBannerState: SecurityBannerState,
        /** GUA FORK: true while the encryption setup banner's repair is running. */
        val isFinishingEncryptionSetup: Boolean = false,
        override val encryptionSetupNeedsReset: Boolean = false,
    ) : RoomListContentState

    data class Rooms(
        val securityBannerState: SecurityBannerState,
        /** GUA FORK: true while the encryption setup banner's repair is running. */
        val isFinishingEncryptionSetup: Boolean = false,
        override val encryptionSetupNeedsReset: Boolean = false,
        val fullScreenIntentPermissionsState: FullScreenIntentPermissionsState,
        val batteryOptimizationState: BatteryOptimizationState,
        val showNewNotificationSoundBanner: Boolean,
        val showUnreadCount: Boolean,
        val summaries: ImmutableList<RoomListRoomSummary>,
        val seenRoomInvites: ImmutableSet<RoomId>,
    ) : RoomListContentState
}
