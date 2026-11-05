/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import io.element.android.libraries.chatbackground.api.ChatBackgroundPreset

sealed interface ChatBackgroundEvent {
    data class SelectPreset(val preset: ChatBackgroundPreset) : ChatBackgroundEvent
    data object ChoosePhoto : ChatBackgroundEvent
    data class SetPhotoDim(val dim: Double) : ChatBackgroundEvent

    /** Moves the photo so that [x] and [y], from 0 to 1, say where it sits when it overflows the frame. */
    data class SetPhotoAlign(val x: Double, val y: Double) : ChatBackgroundEvent
    data class SetDarkPreview(val isDark: Boolean) : ChatBackgroundEvent
    data object DiscardPhoto : ChatBackgroundEvent
    data object Apply : ChatBackgroundEvent
    data object Remove : ChatBackgroundEvent
    data object ClearError : ChatBackgroundEvent
}
