/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.join.JoinRule
import org.junit.Test
import uniffi.matrix_sdk_ui.MembershipChangeFilter
import uniffi.matrix_sdk_ui.TimelineEventCondition
import uniffi.matrix_sdk_ui.TimelineEventFilter
import uniffi.ruma_events.TimelineEventType

class RustTimelineEventFilterFactoryTest {
    private val factory = RustTimelineEventFilterFactory()

    @Test
    fun `custom state events are not excluded, so the chat background notice reaches the timeline`() {
        val filter = factory.create(joinRule = JoinRule.Invite, isEncrypted = true, excludedStateTypes = emptyList())

        assertThat(filter).isEqualTo(TimelineEventFilter.Exclude(emptyList()))
    }

    @Test
    fun `excluded state types and public room membership churn are still excluded`() {
        val filter = factory.create(joinRule = JoinRule.Public, isEncrypted = false, excludedStateTypes = listOf(StateEventType.RoomTopic))

        // TimelineEventType compares through the native library, so the event type is checked by identity.
        val conditions = (filter as TimelineEventFilter.Exclude).v1
        assertThat((conditions.first() as TimelineEventCondition.EventType).v1).isSameInstanceAs(TimelineEventType.RoomTopic)
        assertThat(conditions.drop(1)).containsExactly(
            TimelineEventCondition.MembershipChange(MembershipChangeFilter.JOIN),
            TimelineEventCondition.MembershipChange(MembershipChangeFilter.LEAVE),
            TimelineEventCondition.ProfileChange,
        ).inOrder()
    }
}
