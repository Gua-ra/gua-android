/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.findfriends.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.findfriends.api.FindFriendsEntryPoint
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.StartDMResult
import io.element.android.libraries.matrix.api.room.startDM
import io.element.android.libraries.permissions.api.PermissionsEvent
import io.element.android.libraries.permissions.api.PermissionsPresenter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/**
 * GUA FORK: Molecule presenter for the Find friends screen. Android counterpart of iOS
 * `FindFriendsScreenViewModel`.
 *
 * Drives the READ_CONTACTS permission flow, runs [ContactDiscoveryService] once permission is
 * granted, and starts (or reuses) a DM with the selected contact. Navigation results are forwarded
 * to the [callback] (the surrounding Appyx flow), mirroring iOS' action subject.
 */
@AssistedInject
class FindFriendsPresenter(
    @Assisted private val callback: FindFriendsEntryPoint.Callback,
    private val contactDiscoveryService: ContactDiscoveryService,
    private val matrixClient: MatrixClient,
    permissionsPresenterFactory: PermissionsPresenter.Factory,
) : Presenter<FindFriendsState> {
    @AssistedFactory
    interface Factory {
        fun create(callback: FindFriendsEntryPoint.Callback): FindFriendsPresenter
    }

    private val contactsPermissionPresenter: PermissionsPresenter =
        permissionsPresenterFactory.create(android.Manifest.permission.READ_CONTACTS)

    @Composable
    override fun present(): FindFriendsState {
        val coroutineScope = rememberCoroutineScope()
        val permissionsState = contactsPermissionPresenter.present()

        var phase by remember { mutableStateOf(FindFriendsPhase.Loading) }
        var contacts by remember { mutableStateOf(persistentListOf<DiscoveredContact>().toImmutableList()) }
        var startingChatUserId by remember { mutableStateOf<UserId?>(null) }
        // Bumped to force a re-run of discovery on Retry.
        var discoverNonce by remember { mutableStateOf(0) }

        // Once permission flips to granted, (re)run discovery; otherwise reflect the permission gate.
        LaunchedEffect(permissionsState.permissionGranted, discoverNonce) {
            if (!permissionsState.permissionGranted) {
                phase = if (permissionsState.permissionAlreadyDenied) {
                    FindFriendsPhase.PermissionDenied
                } else {
                    FindFriendsPhase.NeedsPermission
                }
                return@LaunchedEffect
            }

            phase = FindFriendsPhase.Loading
            phase = when (val result = contactDiscoveryService.discover()) {
                is ContactDiscoveryResult.Success -> {
                    contacts = result.contacts.toImmutableList()
                    if (result.contacts.isEmpty()) FindFriendsPhase.Empty else FindFriendsPhase.Loaded
                }
                ContactDiscoveryResult.NoContactsWithNumbers -> {
                    contacts = persistentListOf<DiscoveredContact>().toImmutableList()
                    FindFriendsPhase.Empty
                }
                ContactDiscoveryResult.Failure -> FindFriendsPhase.Error
            }
        }

        fun startChat(contact: DiscoveredContact) {
            if (startingChatUserId != null) return
            startingChatUserId = contact.userId
            coroutineScope.launch {
                try {
                    when (val result = matrixClient.startDM(contact.userId, createIfDmDoesNotExist = true)) {
                        is StartDMResult.Success -> callback.onStartChat(result.roomId)
                        else -> phase = FindFriendsPhase.Error
                    }
                } finally {
                    startingChatUserId = null
                }
            }
        }

        fun handleEvent(event: FindFriendsEvents) {
            when (event) {
                FindFriendsEvents.RequestPermission ->
                    permissionsState.eventSink(PermissionsEvent.RequestPermissions)
                FindFriendsEvents.OpenSettings ->
                    permissionsState.eventSink(PermissionsEvent.OpenSystemSettingAndCloseDialog)
                FindFriendsEvents.Retry -> discoverNonce++
                is FindFriendsEvents.StartChat -> startChat(event.contact)
                is FindFriendsEvents.OpenProfile -> callback.onOpenProfile(event.contact.userId)
            }
        }

        return FindFriendsState(
            phase = phase,
            contacts = contacts,
            startingChatUserId = startingChatUserId,
            eventSink = ::handleEvent,
        )
    }
}
