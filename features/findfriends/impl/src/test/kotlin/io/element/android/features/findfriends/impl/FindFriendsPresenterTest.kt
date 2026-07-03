/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.findfriends.api.FindFriendsEntryPoint
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.permissions.api.aPermissionsState
import io.element.android.libraries.permissions.test.FakePermissionsPresenter
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FindFriendsPresenterTest {
    @Test
    fun `granted permission with matches lands on Loaded`() = runTest {
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.Success(aDiscoveredContactList()) },
        )
        presenter.test {
            val state = consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Loaded }.last()
            assertThat(state.contacts).hasSize(3)
            assertThat(state.contacts.first().handle).isEqualTo("@alice")
        }
    }

    @Test
    fun `granted permission with no matches lands on Empty`() = runTest {
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.Success(emptyList()) },
        )
        presenter.test {
            val state = consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Empty }.last()
            assertThat(state.contacts).isEmpty()
        }
    }

    @Test
    fun `no contacts with numbers lands on Empty`() = runTest {
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.NoContactsWithNumbers },
        )
        presenter.test {
            assertThat(consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Empty }.last().phase)
                .isEqualTo(FindFriendsPhase.Empty)
        }
    }

    @Test
    fun `discovery failure lands on Error`() = runTest {
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.Failure },
        )
        presenter.test {
            assertThat(consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Error }.last().phase)
                .isEqualTo(FindFriendsPhase.Error)
        }
    }

    @Test
    fun `permission not yet granted lands on NeedsPermission`() = runTest {
        val presenter = createPresenter(permissionGranted = false)
        presenter.test {
            assertThat(consumeItemsUntilPredicate { it.phase == FindFriendsPhase.NeedsPermission }.last().phase)
                .isEqualTo(FindFriendsPhase.NeedsPermission)
        }
    }

    @Test
    fun `permanently denied permission lands on PermissionDenied`() = runTest {
        val deniedPresenter = FakePermissionsPresenter(
            aPermissionsState(showDialog = false, permissionGranted = false).copy(permissionAlreadyDenied = true),
        )
        val presenter = createPresenter(permissionsPresenter = deniedPresenter)
        presenter.test {
            assertThat(consumeItemsUntilPredicate { it.phase == FindFriendsPhase.PermissionDenied }.last().phase)
                .isEqualTo(FindFriendsPhase.PermissionDenied)
        }
    }

    @Test
    fun `selecting a contact starts a chat and notifies the callback`() = runTest {
        var startedRoom: RoomId? = null
        val callback = object : FindFriendsEntryPoint.Callback {
            override fun onStartChat(roomId: RoomId) {
                startedRoom = roomId
            }

            override fun onOpenProfile(userId: UserId) = Unit
        }
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.Success(aDiscoveredContactList()) },
            callback = callback,
        )
        presenter.test {
            val loaded = consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Loaded }.last()
            loaded.eventSink(FindFriendsEvents.StartChat(loaded.contacts.first()))
            // Drain emissions: the per-row spinner toggles on then off as the DM is opened.
            consumeItemsUntilTimeout()
            assertThat(startedRoom).isEqualTo(A_ROOM_ID)
        }
    }

    @Test
    fun `opening a profile notifies the callback`() = runTest {
        var openedProfile: UserId? = null
        val callback = object : FindFriendsEntryPoint.Callback {
            override fun onStartChat(roomId: RoomId) = Unit

            override fun onOpenProfile(userId: UserId) {
                openedProfile = userId
            }
        }
        val presenter = createPresenter(
            permissionGranted = true,
            discovery = FakeContactDiscoveryService { ContactDiscoveryResult.Success(aDiscoveredContactList()) },
            callback = callback,
        )
        presenter.test {
            val loaded = consumeItemsUntilPredicate { it.phase == FindFriendsPhase.Loaded }.last()
            loaded.eventSink(FindFriendsEvents.OpenProfile(loaded.contacts.first()))
            assertThat(openedProfile).isEqualTo(loaded.contacts.first().userId)
        }
    }

    private fun createPresenter(
        permissionGranted: Boolean = true,
        permissionsPresenter: FakePermissionsPresenter = FakePermissionsPresenter(
            aPermissionsState(showDialog = false, permissionGranted = permissionGranted),
        ),
        discovery: FakeContactDiscoveryService = FakeContactDiscoveryService(),
        callback: FindFriendsEntryPoint.Callback = NoopCallback,
    ) = FindFriendsPresenter(
        callback = callback,
        contactDiscoveryService = discovery,
        matrixClient = FakeMatrixClient(),
        permissionsPresenterFactory = FakePermissionsPresenterFactory(permissionsPresenter),
    )

    private object NoopCallback : FindFriendsEntryPoint.Callback {
        override fun onStartChat(roomId: RoomId) = Unit
        override fun onOpenProfile(userId: UserId) = Unit
    }
}
