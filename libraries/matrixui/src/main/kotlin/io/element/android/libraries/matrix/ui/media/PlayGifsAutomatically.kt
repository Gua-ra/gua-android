/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.collectAsState
import io.element.android.libraries.matrixmedia.api.LocalPlayGifsAutomatically
import kotlinx.coroutines.flow.StateFlow

/**
 * [LocalPlayGifsAutomatically] for the whole app, following [setting] so every reader updates as soon as it changes.
 * Without it the local keeps its default and the setting has no effect.
 */
@Composable
fun playGifsAutomaticallyFrom(setting: StateFlow<Boolean>): ProvidedValue<Boolean> =
    LocalPlayGifsAutomatically provides setting.collectAsState().value
