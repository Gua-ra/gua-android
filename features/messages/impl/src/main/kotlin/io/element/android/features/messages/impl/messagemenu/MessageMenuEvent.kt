/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import io.element.android.features.messages.impl.UserEventPermissions
import io.element.android.features.messages.impl.timeline.model.TimelineItem

sealed interface MessageMenuEvent {
    data class Show(
        val event: TimelineItem.Event,
        val gesture: MessageMenuGesture,
        val userEventPermissions: UserEventPermissions,
    ) : MessageMenuEvent

    data object Dismiss : MessageMenuEvent

    data class SelectText(val event: TimelineItem.Event) : MessageMenuEvent

    data object DismissTextSelection : MessageMenuEvent
}
