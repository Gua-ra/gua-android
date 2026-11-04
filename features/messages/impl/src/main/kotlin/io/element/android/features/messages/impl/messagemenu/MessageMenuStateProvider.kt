/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

fun aMessageMenuState(
    target: MessageMenuState.Target = MessageMenuState.Target.None,
    selectableText: String? = null,
    eventSink: (MessageMenuEvent) -> Unit = {},
) = MessageMenuState(
    target = target,
    selectableText = selectableText,
    eventSink = eventSink,
)
