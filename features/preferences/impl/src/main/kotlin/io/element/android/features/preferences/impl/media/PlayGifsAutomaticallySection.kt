/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text

/** Sits below the upload quality settings, so it waits for them to load instead of jumping down once they do. */
@Composable
internal fun PlayGifsAutomaticallySection(state: MediaSettingsState) {
    if (state.mediaOptimizationState == null) return
    PreferenceCategory {
        ListItem(
            content = {
                Text(text = stringResource(id = R.string.gua_media_settings_play_gifs_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.gua_media_settings_play_gifs_description))
            },
            trailingContent = ListItemContent.Switch(
                checked = state.playGifsAutomatically,
            ),
            onClick = {
                state.eventSink(MediaSettingsEvent.SetPlayGifsAutomatically(!state.playGifsAutomatically))
            },
        )
    }
}
