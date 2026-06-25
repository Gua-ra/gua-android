/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.findfriends.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar

/**
 * GUA FORK: stateless Find friends UI. Android counterpart of iOS `FindFriendsScreen`. Renders the
 * needs-permission / permission-denied / loading / empty / error / results states, and surfaces the
 * homeserver-abstracted handle as the only id shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindFriendsView(
    state: FindFriendsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_find_friends_title),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.phase) {
                FindFriendsPhase.Loading -> MessageState(
                    title = stringResource(R.string.screen_find_friends_loading),
                    showSpinner = true,
                )
                FindFriendsPhase.NeedsPermission -> MessageState(
                    icon = CompoundIcons.UserProfile(),
                    title = stringResource(R.string.screen_find_friends_permission_title),
                    message = stringResource(R.string.screen_find_friends_permission_message),
                    actionTitle = stringResource(R.string.screen_find_friends_permission_action),
                    onAction = { state.eventSink(FindFriendsEvents.RequestPermission) },
                )
                FindFriendsPhase.PermissionDenied -> MessageState(
                    icon = CompoundIcons.UserProfile(),
                    title = stringResource(R.string.screen_find_friends_permission_title),
                    message = stringResource(R.string.screen_find_friends_permission_message),
                    actionTitle = stringResource(R.string.screen_find_friends_permission_denied_action),
                    onAction = { state.eventSink(FindFriendsEvents.OpenSettings) },
                )
                FindFriendsPhase.Empty -> MessageState(
                    icon = CompoundIcons.UserAdd(),
                    title = stringResource(R.string.screen_find_friends_empty_title),
                    message = stringResource(R.string.screen_find_friends_empty_message),
                    actionTitle = stringResource(R.string.screen_find_friends_empty_action),
                    onAction = { state.eventSink(FindFriendsEvents.Retry) },
                )
                FindFriendsPhase.Error -> MessageState(
                    icon = CompoundIcons.Error(),
                    title = stringResource(R.string.screen_find_friends_error_title),
                    message = stringResource(R.string.screen_find_friends_error_message),
                    actionTitle = stringResource(R.string.screen_find_friends_error_action),
                    onAction = { state.eventSink(FindFriendsEvents.Retry) },
                )
                FindFriendsPhase.Loaded -> ContactsList(state)
            }
        }
    }
}

@Composable
private fun ContactsList(state: FindFriendsState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListSectionHeader(
                title = pluralStringResource(
                    R.plurals.screen_find_friends_results_header_plural,
                    state.contacts.size,
                    state.contacts.size,
                ),
                hasDivider = false,
            )
        }
        items(state.contacts, key = { it.userId.value }) { contact ->
            ContactRow(
                contact = contact,
                isStartingChat = state.startingChatUserId == contact.userId,
                isBusy = state.startingChatUserId != null,
                onProfileClick = { state.eventSink(FindFriendsEvents.OpenProfile(contact)) },
                onRowClick = { state.eventSink(FindFriendsEvents.StartChat(contact)) },
            )
        }
    }
}

@Composable
private fun ContactRow(
    contact: DiscoveredContact,
    isStartingChat: Boolean,
    isBusy: Boolean,
    onProfileClick: () -> Unit,
    onRowClick: () -> Unit,
) {
    val viewProfileLabel = stringResource(R.string.a11y_find_friends_view_profile, contact.localName)
    val startChatLabel = stringResource(R.string.a11y_find_friends_start_chat, contact.localName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            modifier = Modifier
                .clickable(enabled = !isBusy, onClick = onProfileClick)
                .semantics { contentDescription = viewProfileLabel },
            avatarData = AvatarData(
                id = contact.userId.value,
                name = contact.localName,
                url = contact.avatarUrl,
                size = AvatarSize.UserListItem,
            ),
            avatarType = AvatarType.User,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.localName,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = contact.handle,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        if (isStartingChat) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                modifier = Modifier.semantics { contentDescription = startChatLabel },
                imageVector = CompoundIcons.Chat(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    message: String? = null,
    showSpinner: Boolean = false,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.size(16.dp))
        } else if (icon != null) {
            Icon(
                modifier = Modifier.size(44.dp),
                imageVector = icon,
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingSmMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionTitle != null && onAction != null) {
            Spacer(modifier = Modifier.size(24.dp))
            Button(
                text = actionTitle,
                onClick = onAction,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun FindFriendsViewPreview(@PreviewParameter(FindFriendsStateProvider::class) state: FindFriendsState) =
    ElementPreview {
        FindFriendsView(
            state = state,
            onBackClick = {},
        )
    }
