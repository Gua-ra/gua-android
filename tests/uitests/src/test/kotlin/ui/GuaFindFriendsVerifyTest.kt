/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.findfriends.impl.DiscoveredContact
import io.element.android.features.findfriends.impl.FindFriendsPhase
import io.element.android.features.findfriends.impl.FindFriendsState
import io.element.android.features.findfriends.impl.FindFriendsView
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK S12 verification: records focused screenshots of the Find friends contact-discovery
 * screen (Android port of iOS `FindFriendsScreen`) so the visual can be reviewed without recording
 * the whole golden set. Mirrors the precedent of [GuaPhoneEntryVerifyTest].
 *
 * Covers the key states (results / empty / permission-denied) and confirms that only the
 * homeserver-abstracted handle (e.g. "@alice") is shown, never a ":homeserver" suffix. Records to
 * its own snapshot files and does not touch the shared preview-driven golden set.
 */
class GuaFindFriendsVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaFindFriendsResults() {
        paparazzi.snapshot {
            ElementTheme {
                FindFriendsView(
                    state = aState(phase = FindFriendsPhase.Loaded, contacts = sampleContacts()),
                    onBackClick = {},
                )
            }
        }
    }

    @Test
    fun guaFindFriendsEmpty() {
        paparazzi.snapshot {
            ElementTheme {
                FindFriendsView(
                    state = aState(phase = FindFriendsPhase.Empty, contacts = emptyList()),
                    onBackClick = {},
                )
            }
        }
    }

    @Test
    fun guaFindFriendsPermissionDenied() {
        paparazzi.snapshot {
            ElementTheme {
                FindFriendsView(
                    state = aState(phase = FindFriendsPhase.PermissionDenied, contacts = emptyList()),
                    onBackClick = {},
                )
            }
        }
    }

    private fun aState(phase: FindFriendsPhase, contacts: List<DiscoveredContact>) = FindFriendsState(
        phase = phase,
        contacts = contacts.toImmutableList(),
        startingChatUserId = null,
        eventSink = {},
    )

    private fun sampleContacts(): List<DiscoveredContact> = persistentListOf(
        DiscoveredContact(localName = "Alice Martins", userId = UserId("@alice:gua.global"), handle = "@alice", avatarUrl = null),
        DiscoveredContact(localName = "Bruno Costa", userId = UserId("@bruno:gua.global"), handle = "@bruno", avatarUrl = null),
        DiscoveredContact(localName = "Carla Dias", userId = UserId("@carla:gua.global"), handle = "@carla", avatarUrl = null),
    )
}
