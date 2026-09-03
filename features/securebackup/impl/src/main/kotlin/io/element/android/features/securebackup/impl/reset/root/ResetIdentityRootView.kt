/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.securebackup.impl.R
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun ResetIdentityRootView(
    state: ResetIdentityRootState,
    onContinue: () -> Unit,
    onRecoverFromOtherDevice: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowStepPage(
        modifier = modifier,
        iconStyle = BigIcon.Style.AlertSolid,
        // GUA FORK: when the keys can come from another device, this screen is about
        // getting them back, not about what is lost.
        title = stringResource(
            if (state.canRecoverFromOtherDevice) {
                R.string.gua_encryption_recover_from_other_device_title
            } else {
                R.string.gua_encryption_reset_required_title
            }
        ),
        isScrollable = true,
        content = { Content(canRecoverFromOtherDevice = state.canRecoverFromOtherDevice) },
        buttons = {
            // GUA FORK: offered only when another device of this account holds the keys. It
            // brings the messages here without resetting anything; otherwise the reset is the
            // only way forward and the sole option shown. The reset goes straight through: this
            // screen already names what is lost and its button is destructive.
            if (state.canRecoverFromOtherDevice) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.gua_encryption_recover_from_other_device_action),
                    onClick = onRecoverFromOtherDevice,
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.gua_encryption_reset_required_action),
                    onClick = onContinue,
                    destructive = true,
                )
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.gua_encryption_reset_required_action),
                    onClick = onContinue,
                    destructive = true,
                )
            }
        },
        onBackClick = onBack,
    )
}

@Composable
private fun Content(canRecoverFromOtherDevice: Boolean) {
    Column(
        modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // GUA FORK: one plain sentence about what is lost, instead of three bullets of
        // upstream jargon about identities and recovery keys the user has never seen. When the
        // keys can be fetched from another device, saying the backup must be reset would be
        // untrue, and the button below offers the other way out.
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(
                if (canRecoverFromOtherDevice) {
                    R.string.gua_encryption_recover_from_other_device_message
                } else {
                    R.string.gua_encryption_reset_required_message
                }
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ResetIdentityRootViewPreview(@PreviewParameter(ResetIdentityRootStateProvider::class) state: ResetIdentityRootState) {
    ElementPreview {
        ResetIdentityRootView(
            state = state,
            onContinue = {},
            onRecoverFromOtherDevice = {},
            onBack = {},
        )
    }
}
