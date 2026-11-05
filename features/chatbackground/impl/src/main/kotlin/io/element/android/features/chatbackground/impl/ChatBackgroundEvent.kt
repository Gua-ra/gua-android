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
    data class SetPhotoDim(val dim: Int) : ChatBackgroundEvent
    data class SetPhotoAlign(val x: Int, val y: Int) : ChatBackgroundEvent
    data class SetDarkPreview(val isDark: Boolean) : ChatBackgroundEvent
    data object DiscardPhoto : ChatBackgroundEvent
    data object Apply : ChatBackgroundEvent
    data object Remove : ChatBackgroundEvent
    data object ClearError : ChatBackgroundEvent
}
