/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatBubbleColor
import io.element.android.libraries.designsystem.theme.messageFromMeBackground

@Composable
@ReadOnlyComposable
fun ChatBubbleColor.toColor(): Color = Color(color(ElementTheme.isLightTheme))

@Composable
@ReadOnlyComposable
fun ChatBubbleColor?.outgoingBubbleColor(): Color = this?.toColor() ?: ElementTheme.colors.messageFromMeBackground
