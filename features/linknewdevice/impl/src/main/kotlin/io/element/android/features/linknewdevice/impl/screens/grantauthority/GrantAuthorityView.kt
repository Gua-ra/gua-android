/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.grantauthority

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.linknewdevice.impl.R
import io.element.android.libraries.designsystem.components.async.AsyncLoading
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * GUA FORK: the grant offer (ADM-009 decision 5).
 *
 * It names the new device, says in plain words what the grant lets it do, and says what the waiting period
 * afterwards means. Declining sits next to accepting rather than under it: the new device already works.
 */
@Composable
fun GrantAuthorityView(
    state: GrantAuthorityState,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (state.phase) {
                GrantAuthorityPhase.Prompt -> {
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_header),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            id = R.string.screen_link_grant_authority_message,
                            state.deviceLabel,
                        ),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_quarantine_notice),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Button(
                        text = stringResource(id = R.string.screen_link_grant_authority_action),
                        onClick = { eventSink(GrantAuthorityEvent.Grant) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    )
                    TextButton(
                        text = stringResource(id = R.string.screen_link_grant_authority_skip_action),
                        onClick = { eventSink(GrantAuthorityEvent.Skip) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                GrantAuthorityPhase.Compare -> {
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_compare_header),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            id = R.string.screen_link_grant_authority_compare_message,
                            state.deviceLabel,
                        ),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    // The value itself, large and on its own line, because the whole point is that two people
                    // read it out to each other and both phones computed it from the same key.
                    Text(
                        text = state.fingerprint,
                        style = ElementTheme.typography.fontHeadingMdBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_compare_warning),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(id = R.string.screen_link_grant_authority_compare_confirm))
                        },
                        trailingContent = ListItemContent.Switch(checked = state.fingerprintConfirmed),
                        onClick = {
                            eventSink(GrantAuthorityEvent.ConfirmFingerprint(!state.fingerprintConfirmed))
                        },
                    )
                    Button(
                        text = stringResource(id = CommonStrings.action_continue),
                        // The same rule the presenter enforces and the manager enforces again, because this
                        // one is only a button.
                        enabled = state.canContinueFromCompare,
                        onClick = { eventSink(GrantAuthorityEvent.ContinueFromCompare) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                    TextButton(
                        text = stringResource(id = R.string.screen_link_grant_authority_skip_action),
                        onClick = { eventSink(GrantAuthorityEvent.Skip) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                GrantAuthorityPhase.StepUp,
                GrantAuthorityPhase.Submitting -> {
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_pin_header),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.screen_link_grant_authority_pin_footer),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                    TextField(
                        value = state.pin,
                        onValueChange = { eventSink(GrantAuthorityEvent.PinChanged(it)) },
                        enabled = !state.isWorking,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                    state.errorMessage?.let { message ->
                        Text(
                            text = stringResource(id = message),
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textCriticalPrimary,
                        )
                    }
                    Button(
                        text = stringResource(id = CommonStrings.action_confirm),
                        enabled = state.canSubmit,
                        onClick = { eventSink(GrantAuthorityEvent.Submit) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                    TextButton(
                        text = stringResource(id = R.string.screen_link_grant_authority_skip_action),
                        onClick = { eventSink(GrantAuthorityEvent.Skip) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                GrantAuthorityPhase.Done -> AsyncLoading()
            }
        }
    }
}
