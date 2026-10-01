/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.home.impl.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.home.impl.accountrecovery.aHiddenAccountRecoveryBannerState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.SecurityBannerState
import io.element.android.features.home.impl.roomlist.aRoomsContentState
import io.element.android.features.home.impl.roomlist.anEmptyContentState
import io.element.android.features.home.impl.spacefilters.aDisabledSpaceFiltersState
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class RoomListContentViewEncryptionResetTest : RobolectricTest() {
    @Test
    fun `a reset verdict on the rooms list is consumed, not left latched`() = runAndroidComposeUiTest<ComponentActivity> {
        val events = mutableListOf<RoomListEvent>()
        var navigated = 0

        setContent {
            RoomListContentView(
                contentState = aRoomsContentState(
                    securityBannerState = SecurityBannerState.RecoveryKeyConfirmation,
                ).copy(encryptionSetupNeedsReset = true),
                accountRecoveryBannerState = aHiddenAccountRecoveryBannerState(),
                filtersState = aRoomListFiltersState(),
                spaceFiltersState = aDisabledSpaceFiltersState(),
                lazyListState = rememberLazyListState(),
                hideInvitesAvatars = false,
                eventSink = { events.add(it) },
                onConfirmRecoveryKeyClick = { navigated++ },
                onRoomClick = {},
                onCreateRoomClick = {},
                contentPadding = PaddingValues(),
            )
        }

        assert(navigated == 1) { "expected to navigate once, navigated $navigated times" }
        assert(events.contains(RoomListEvent.EncryptionResetNavigated)) {
            "expected the verdict to be consumed, got $events"
        }
    }

    @Test
    fun `a reset verdict on the empty list is consumed too`() = runAndroidComposeUiTest<ComponentActivity> {
        val events = mutableListOf<RoomListEvent>()
        var navigated = 0

        setContent {
            RoomListContentView(
                contentState = anEmptyContentState(
                    securityBannerState = SecurityBannerState.RecoveryKeyConfirmation,
                ).copy(encryptionSetupNeedsReset = true),
                accountRecoveryBannerState = aHiddenAccountRecoveryBannerState(),
                filtersState = aRoomListFiltersState(),
                spaceFiltersState = aDisabledSpaceFiltersState(),
                lazyListState = rememberLazyListState(),
                hideInvitesAvatars = false,
                eventSink = { events.add(it) },
                onConfirmRecoveryKeyClick = { navigated++ },
                onRoomClick = {},
                onCreateRoomClick = {},
                contentPadding = PaddingValues(),
            )
        }

        assert(navigated == 1) { "expected to navigate once, navigated $navigated times" }
        assert(events.contains(RoomListEvent.EncryptionResetNavigated)) {
            "expected the verdict to be consumed, got $events"
        }
    }

    @Test
    fun `no verdict means no navigation and no event`() = runAndroidComposeUiTest<ComponentActivity> {
        val events = mutableListOf<RoomListEvent>()
        var navigated = 0

        setContent {
            RoomListContentView(
                contentState = aRoomsContentState(
                    securityBannerState = SecurityBannerState.RecoveryKeyConfirmation,
                ) as RoomListContentState,
                accountRecoveryBannerState = aHiddenAccountRecoveryBannerState(),
                filtersState = aRoomListFiltersState(),
                spaceFiltersState = aDisabledSpaceFiltersState(),
                lazyListState = rememberLazyListState(),
                hideInvitesAvatars = false,
                eventSink = { events.add(it) },
                onConfirmRecoveryKeyClick = { navigated++ },
                onRoomClick = {},
                onCreateRoomClick = {},
                contentPadding = PaddingValues(),
            )
        }

        assert(navigated == 0) { "did not expect to navigate, navigated $navigated times" }
        assert(!events.contains(RoomListEvent.EncryptionResetNavigated)) {
            "did not expect a consume event, got $events"
        }
    }
}
