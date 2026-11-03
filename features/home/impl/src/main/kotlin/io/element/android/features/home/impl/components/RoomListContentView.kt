/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.accountrecovery.AccountRecoveryBannerState
import io.element.android.features.home.impl.accountrecovery.aHiddenAccountRecoveryBannerState
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.filters.RoomListFilter
import io.element.android.features.home.impl.filters.RoomListFiltersEmptyStateResources
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.filters.selection.FilterSelectionState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.roomlist.RoomListBanner
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListContentStatePreviewParam
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.toRoomListBanner
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import io.element.android.libraries.fullscreenintent.api.FullScreenIntentPermissionsEvent
import io.element.android.libraries.push.api.battery.BatteryOptimizationEvent
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList

@Composable
fun RoomListContentView(
    contentState: RoomListContentState,
    // GUA FORK: shown above every other banner, whatever the content state, and not dismissible.
    accountRecoveryBannerState: AccountRecoveryBannerState,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    lazyListState: LazyListState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    onCreateRoomClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // GUA FORK: navigation is driven by the repair's verdict, not by the tap, and it lives HERE --
    // once, above the branch -- rather than inside each state's own composable.
    //
    // It used to be written twice. EmptyView consumed the flag by sending EncryptionResetNavigated;
    // RoomsView, the branch every account with any chats takes, navigated and never sent it. The
    // flag stayed true, so its LaunchedEffect key never changed again, and every later tap of
    // Finish setup set an already-true flag and did nothing. That is the dead button.
    val onNeedsReset by rememberUpdatedState(onConfirmRecoveryKeyClick)
    val onNavigated by rememberUpdatedState(eventSink)
    LaunchedEffect(contentState.encryptionSetupNeedsReset) {
        if (contentState.encryptionSetupNeedsReset) {
            // Consume first: the flag is one-shot, and leaving it set is what wedged the button.
            onNavigated(RoomListEvent.EncryptionResetNavigated)
            onNeedsReset()
        }
    }

    AccountRecoveryCancelConfirmation(state = accountRecoveryBannerState)

    when (contentState) {
        is RoomListContentState.Skeleton -> {
            SkeletonView(
                modifier = modifier,
                count = contentState.count,
                accountRecoveryBannerState = accountRecoveryBannerState,
                contentPadding = contentPadding,
            )
        }
        is RoomListContentState.Empty -> {
            EmptyView(
                modifier = modifier.padding(contentPadding),
                state = contentState,
                accountRecoveryBannerState = accountRecoveryBannerState,
                eventSink = eventSink,
                onCreateRoomClick = onCreateRoomClick,
            )
        }
        is RoomListContentState.Rooms -> {
            RoomsView(
                modifier = modifier,
                state = contentState,
                accountRecoveryBannerState = accountRecoveryBannerState,
                hideInvitesAvatars = hideInvitesAvatars,
                filtersState = filtersState,
                spaceFiltersState = spaceFiltersState,
                eventSink = eventSink,
                onRoomClick = onRoomClick,
                lazyListState = lazyListState,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun SkeletonView(
    count: Int,
    accountRecoveryBannerState: AccountRecoveryBannerState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        // GUA FORK: the first sync can take a while, and this warning should not wait for it.
        if (accountRecoveryBannerState.pendingRecovery != null) {
            item {
                AccountRecoveryBanner(state = accountRecoveryBannerState)
            }
        }
        repeat(count) { index ->
            item {
                RoomSummaryPlaceholderRow()
                if (index != count - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Ref: https://www.figma.com/design/pDlJZGBsri47FNTXMnEdXB/Compound-Android-Templates?node-id=8-4656
 */
@Composable
private fun EmptyView(
    state: RoomListContentState.Empty,
    accountRecoveryBannerState: AccountRecoveryBannerState,
    eventSink: (RoomListEvent) -> Unit,
    onCreateRoomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        val text = buildString {
            append(stringResource(R.string.screen_roomlist_empty_title))
            append("\n")
            append(stringResource(R.string.screen_roomlist_empty_message))
        }
        EmptyScaffold(
            title = null,
            subtitle = text,
            bottomPart = {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    text = stringResource(CommonStrings.action_start_chat),
                    leadingIcon = IconSource.Vector(CompoundIcons.Chat()),
                    onClick = onCreateRoomClick,
                )
            },
            modifier = Modifier.align(Alignment.Center),
        )
        Column {
            AccountRecoveryBanner(state = accountRecoveryBannerState)
            RoomListBannerContainer(
                banner = state.securityBannerState.toRoomListBanner(),
            ) { banner ->
                RoomListBannerView(
                    banner = banner,
                    isFinishingEncryptionSetup = state.isFinishingEncryptionSetup,
                    onActionClick = {
                        when (banner) {
                            RoomListBanner.SetUpRecovery,
                            RoomListBanner.RecoveryKeyConfirmation -> eventSink(RoomListEvent.FinishEncryptionSetup)
                            else -> Unit
                        }
                    },
                    onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                )
            }
        }
    }
}

@Composable
private fun RoomsView(
    state: RoomListContentState.Rooms,
    accountRecoveryBannerState: AccountRecoveryBannerState,
    hideInvitesAvatars: Boolean,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val isSpaceFilterSelected = spaceFiltersState is SpaceFiltersState.Selected
    val hasAnyFilterSelected = filtersState.hasAnyFilterSelected || isSpaceFilterSelected
    if (state.summaries.isEmpty() && hasAnyFilterSelected) {
        if (accountRecoveryBannerState.pendingRecovery != null) {
            // GUA FORK: a filter with no matches must not hide the recovery warning.
            Column(modifier = modifier.fillMaxSize()) {
                AccountRecoveryBanner(state = accountRecoveryBannerState)
                EmptyViewForFilterStates(
                    selectedFilters = filtersState.selectedFilters(),
                    isSpaceFilterSelected = isSpaceFilterSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        } else {
            EmptyViewForFilterStates(
                selectedFilters = filtersState.selectedFilters(),
                isSpaceFilterSelected = isSpaceFilterSelected,
                modifier = modifier.fillMaxSize()
            )
        }
    } else {
        RoomsViewList(
            state = state,
            accountRecoveryBannerState = accountRecoveryBannerState,
            hideInvitesAvatars = hideInvitesAvatars,
            eventSink = eventSink,
            onRoomClick = onRoomClick,
            contentPadding = contentPadding,
            lazyListState = lazyListState,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RoomsViewList(
    state: RoomListContentState.Rooms,
    accountRecoveryBannerState: AccountRecoveryBannerState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val banner = state.toRoomListBanner()
    // The banner slot must never be 0px high, else the LazyColumn considers that it is not visible and it is
    // removed from the composition. A new banner would then be added above the visible area of the list.
    val bannerSlotMinHeight = with(LocalDensity.current) { 1.toDp() }
    OnVisibleRangeChangeEffect(lazyListState) { visibleRange ->
        // The first item of the list is the banner slot, remove it to get the range of the rooms
        val roomsRange = (visibleRange.first - 1).coerceAtLeast(0)..<visibleRange.last
        eventSink(RoomListEvent.UpdateVisibleRange(roomsRange))
    }
    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        // The banner slot is always the first item of the list, even when there is no banner to display,
        // so that the rooms keep the same index when a banner is added or removed. Since the rooms do not
        // have a key, this is required to keep the scroll position.
        item {
            Column {
                // GUA FORK: above the other banners and independent of them, so neither the encryption
                // banner nor any dismissal can push it out.
                AccountRecoveryBanner(state = accountRecoveryBannerState)
                RoomListBannerContainer(
                    banner = banner,
                    modifier = Modifier.heightIn(min = bannerSlotMinHeight),
                ) { displayedBanner ->
                    RoomListBannerView(
                        banner = displayedBanner,
                        isFinishingEncryptionSetup = state.isFinishingEncryptionSetup,
                        onActionClick = {
                            when (displayedBanner) {
                                RoomListBanner.SetUpRecovery,
                                RoomListBanner.RecoveryKeyConfirmation -> eventSink(RoomListEvent.FinishEncryptionSetup)
                                RoomListBanner.FullScreenIntentPermission ->
                                    state.fullScreenIntentPermissionsState.eventSink(FullScreenIntentPermissionsEvent.OpenSettings)
                                RoomListBanner.BatteryOptimization ->
                                    state.batteryOptimizationState.eventSink(BatteryOptimizationEvent.RequestDisableOptimizations)
                                RoomListBanner.NewNotificationSound,
                                RoomListBanner.SwipeAvatar -> Unit
                            }
                        },
                        onDismissClick = {
                            when (displayedBanner) {
                                RoomListBanner.SetUpRecovery,
                                RoomListBanner.RecoveryKeyConfirmation -> eventSink(RoomListEvent.DismissBanner)
                                RoomListBanner.FullScreenIntentPermission ->
                                    state.fullScreenIntentPermissionsState.eventSink(FullScreenIntentPermissionsEvent.Dismiss)
                                RoomListBanner.BatteryOptimization -> state.batteryOptimizationState.eventSink(BatteryOptimizationEvent.Dismiss)
                                RoomListBanner.NewNotificationSound -> eventSink(RoomListEvent.DismissNewNotificationSoundBanner)
                                RoomListBanner.SwipeAvatar -> eventSink(RoomListEvent.DismissSwipeAvatarBanner)
                            }
                        },
                    )
                }
            }
        }
        // Note: do not use a key for the LazyColumn, or the scroll will not behave as expected if a room
        // is moved to the top of the list.
        itemsIndexed(
            items = state.summaries,
            contentType = { _, room -> room.contentType() },
        ) { index, room ->
            RoomSummaryRow(
                room = room,
                hideInviteAvatars = hideInvitesAvatars,
                isInviteSeen = room.displayType == RoomSummaryDisplayType.INVITE &&
                    state.seenRoomInvites.contains(room.roomId),
                showUnreadCount = state.showUnreadCount,
                showAllActivity = state.showAllActivity,
                onClick = onRoomClick,
                eventSink = eventSink,
            )
            if (index != state.summaries.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Displays the [banner] at the top of the room list, and animates every change: when the banner is
 * dismissed, when it is replaced by another banner, and when a banner appears.
 * The size of the slot is animated, so that the items below it are not moved abruptly.
 *
 * @param banner the banner to display, or null if there is no banner to display.
 * @param modifier the modifier to apply to the slot.
 * @param content renders the given banner.
 */
@Composable
private fun RoomListBannerContainer(
    banner: RoomListBanner?,
    modifier: Modifier = Modifier,
    content: @Composable (RoomListBanner) -> Unit,
) {
    AnimatedContent(
        targetState = banner,
        // Fill the width so that only the height is animated
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            fadeIn(tween(durationMillis = HALF_DURATION_MILLIS, delayMillis = HALF_DURATION_MILLIS)) togetherWith
                fadeOut(tween(durationMillis = HALF_DURATION_MILLIS)) using
                SizeTransform { _, _ -> tween(durationMillis = 2 * HALF_DURATION_MILLIS) }
        },
        contentAlignment = Alignment.Center,
        label = "RoomListBannerContainer",
    ) { targetBanner ->
        if (targetBanner != null) {
            content(targetBanner)
        }
    }
}

private const val HALF_DURATION_MILLIS = 150

@Composable
private fun RoomListBannerView(
    banner: RoomListBanner,
    isFinishingEncryptionSetup: Boolean,
    onActionClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    when (banner) {
        // GUA FORK: both states render the silent-repair banner, so no path reaches the flow that
        // hands a user a recovery key to write down.
        RoomListBanner.SetUpRecovery,
        RoomListBanner.RecoveryKeyConfirmation -> ConfirmRecoveryKeyBanner(
            onContinueClick = onActionClick,
            onDismissClick = onDismissClick,
            isWorking = isFinishingEncryptionSetup,
        )
        RoomListBanner.FullScreenIntentPermission -> FullScreenIntentPermissionBanner(
            onActionClick = onActionClick,
            onDismissClick = onDismissClick,
        )
        RoomListBanner.BatteryOptimization -> BatteryOptimizationBanner(
            onActionClick = onActionClick,
            onDismissClick = onDismissClick,
        )
        RoomListBanner.NewNotificationSound -> NewNotificationSoundBanner(
            onDismissClick = onDismissClick,
        )
        RoomListBanner.SwipeAvatar -> SwipeAvatarBanner(
            onDismissClick = onDismissClick,
        )
    }
}

@Composable
private fun EmptyViewForFilterStates(
    selectedFilters: ImmutableList<RoomListFilter>,
    isSpaceFilterSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val emptyStateResources = RoomListFiltersEmptyStateResources.fromSelectedFilters(selectedFilters, isSpaceFilterSelected) ?: return
    EmptyScaffold(
        title = stringResource(emptyStateResources.title),
        subtitle = stringResource(emptyStateResources.subtitle),
        modifier = modifier,
        bottomPart = {
            // Keep a spacer so that the content is not too low on the screen
            Spacer(modifier = Modifier.height(32.dp))
        }
    )
}

@Composable
private fun EmptyScaffold(
    title: String?,
    subtitle: String,
    modifier: Modifier = Modifier,
    bottomPart: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        title?.let {
            Text(
                text = title,
                style = ElementTheme.typography.fontHeadingMdBold,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = subtitle,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        bottomPart()
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListContentViewPreview(@PreviewParameter(RoomListContentStatePreviewParam::class) state: RoomListContentState) = ElementPreview {
    RoomListContentView(
        contentState = state,
        accountRecoveryBannerState = aHiddenAccountRecoveryBannerState(),
        filtersState = aRoomListFiltersState(
            filterSelectionStates = RoomListFilter.entries.map {
                FilterSelectionState(
                    filter = it,
                    isSelected = true
                )
            }
        ),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        hideInvitesAvatars = false,
        eventSink = {},
        onConfirmRecoveryKeyClick = {},
        onRoomClick = {},
        onCreateRoomClick = {},
        lazyListState = rememberLazyListState(),
        contentPadding = PaddingValues(0.dp),
    )
}
