/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.runtime.Composable

class FakeMessageMenuPresenter(
    private val state: MessageMenuState = aMessageMenuState(),
) : MessageMenuPresenter {
    @Composable
    override fun present(): MessageMenuState = state
}
