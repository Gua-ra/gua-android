/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.chatbackground

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Null keeps the stock outgoing bubble. */
val LocalOutgoingBubbleColor = staticCompositionLocalOf<Color?> { null }
