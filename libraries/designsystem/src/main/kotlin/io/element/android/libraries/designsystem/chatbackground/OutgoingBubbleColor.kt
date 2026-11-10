/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.chatbackground

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The chat theme's outgoing bubble fill, or null for the stock one. */
val LocalOutgoingBubbleColor = staticCompositionLocalOf<Color?> { null }
