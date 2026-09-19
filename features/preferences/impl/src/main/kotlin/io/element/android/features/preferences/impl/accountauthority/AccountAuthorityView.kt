/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.accountauthority

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.components.PinBubbleField
import io.element.android.libraries.designsystem.components.async.AsyncLoading
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.ui.strings.CommonStrings
import java.text.DateFormat
import java.util.Date

/**
 * GUA FORK: the account authority screen (ADM-009).
 *
 * Three rules shape what is drawn here. A pending transition is shown with the time it completes, because
 * the window IS the security of the transition and a screen that hid it would be hiding the only thing the
 * owner can act on. A quarantined device is shown as quarantined, because it can do nothing and counts for
 * nothing. And the recovery artifact is shown on a screen of its own with the consequence spelled out, once,
 * before anything is submitted.
 */
@Composable
fun AccountAuthorityView(
    state: AccountAuthorityState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = state.successMessage?.let { stringResource(id = it) }
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            snackbarHostState.showSnackbar(successMessage)
            eventSink(AccountAuthorityEvent.ClearSuccess)
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (state.phase == AccountAuthorityPhase.Artifact || state.phase == AccountAuthorityPhase.StepUp) {
                eventSink(AccountAuthorityEvent.Cancel)
            } else {
                onBackClick()
            }
        },
        title = stringResource(id = R.string.screen_account_authority_title),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        when (state.phase) {
            AccountAuthorityPhase.Loading -> AsyncLoading()
            AccountAuthorityPhase.Artifact -> ArtifactSection(state = state, eventSink = eventSink)
            AccountAuthorityPhase.StepUp,
            AccountAuthorityPhase.Submitting -> StepUpSection(state = state, eventSink = eventSink)
            AccountAuthorityPhase.Overview -> OverviewSection(state = state, eventSink = eventSink)
        }
    }
}

@Composable
private fun OverviewSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column {
        if (state.unavailable) {
            // Not an error: this is what every deployment answers today, and the wire contract asks a
            // client to read it as "this build does not have the feature".
            Explanation(text = stringResource(id = R.string.screen_account_authority_unavailable))
            return@Column
        }

        state.pendingTransition?.let { pending ->
            ListItem(
                headlineContent = {
                    Text(stringResource(id = R.string.screen_account_authority_adoption_pending_header))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            id = R.string.screen_account_authority_adoption_pending_message,
                            formatTime(pending.effectiveAtEpochSeconds),
                        )
                    )
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Time())),
            )
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_oppose_action)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Close())),
                style = ListItemStyle.Destructive,
                onClick = { eventSink(AccountAuthorityEvent.Oppose) },
            )
            HorizontalDivider()
        }

        if (state.approvals.isNotEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_approvals_header)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Computer())),
            )
            state.approvals.forEach { approval ->
                ApprovalRow(approval = approval, enabled = !state.isWorking, eventSink = eventSink)
            }
            HorizontalDivider()
        }

        if (state.canAdopt) {
            Explanation(text = stringResource(id = R.string.screen_account_authority_bootstrap_message))
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_adopt_action)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
                style = ListItemStyle.Primary,
                onClick = { eventSink(AccountAuthorityEvent.StartAdoption) },
            )
        } else if (state.devices.isNotEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_devices_header)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Devices())),
            )
            state.devices.forEach { device -> DeviceRow(device = device) }
        }

        state.errorMessage?.let { message ->
            Text(
                text = stringResource(id = message),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textCriticalPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * One device the chain has activated, in the chain's own terms.
 *
 * A quarantined device says so and says until when. Drawing it as "active" would be the screen telling the
 * owner they hold two devices when, for every rule that matters, they hold one.
 */
@Composable
private fun DeviceRow(device: AuthorityDevice) {
    ListItem(
        headlineContent = {
            Text(device.label.ifEmpty { stringResource(id = R.string.screen_account_authority_device_unlabelled) })
        },
        supportingContent = {
            Text(
                when {
                    device.isQuarantined && device.quarantineUntilEpochSeconds != null -> stringResource(
                        id = R.string.screen_account_authority_device_quarantined_until,
                        formatTime(device.quarantineUntilEpochSeconds!!),
                    )
                    device.isQuarantined -> stringResource(id = R.string.screen_account_authority_device_quarantined)
                    device.isRevoked -> stringResource(id = R.string.screen_account_authority_device_revoked)
                    else -> stringResource(id = R.string.screen_account_authority_device_active)
                }
            )
        },
        leadingContent = ListItemContent.Icon(
            IconSource.Vector(if (device.isActive) CompoundIcons.Devices() else CompoundIcons.Time())
        ),
    )
}

/**
 * One live browser approval.
 *
 * The action is described in this screen's own words and the four-character code is repeated from the
 * browser. The opaque action id the page chose is never rendered: a malicious page can start an approval,
 * and the one thing that must not happen is its text appearing on the screen that is supposed to be the
 * independent one.
 */
@Composable
private fun ApprovalRow(
    approval: AuthorityApproval,
    enabled: Boolean,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(stringResource(id = R.string.screen_account_authority_approval_message, approval.code))
        },
        supportingContent = { Text(stringResource(id = R.string.screen_account_authority_approval_description)) },
        trailingContent = ListItemContent.Custom { contentEnabled ->
            Button(
                text = stringResource(id = R.string.screen_account_authority_approval_action),
                enabled = enabled && contentEnabled,
                onClick = { eventSink(AccountAuthorityEvent.Approve(approval.approvalId)) },
            )
        },
    )
}

@Composable
private fun ArtifactSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.screen_account_authority_artifact_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_account_authority_artifact_message),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Text(
            text = state.recoveryArtifact.orEmpty(),
            style = ElementTheme.typography.fontBodyLgMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        // The consequence, in plain words, on the same screen as the value: whoever holds this, together
        // with a way into the account, can take it after a wait.
        Text(
            text = stringResource(id = R.string.screen_account_authority_artifact_warning),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
        )
        ListItem(
            headlineContent = { Text(stringResource(id = R.string.screen_account_authority_artifact_confirm)) },
            trailingContent = ListItemContent.Switch(checked = state.artifactConfirmed),
            onClick = { eventSink(AccountAuthorityEvent.ConfirmArtifactStored(!state.artifactConfirmed)) },
        )
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            // Adoption is unreachable without the confirmation. The same rule is enforced in the presenter
            // and again in the manager, because this one is only a button.
            enabled = state.canContinueFromArtifact,
            onClick = { eventSink(AccountAuthorityEvent.ContinueFromArtifact) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun StepUpSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.screen_account_authority_pin_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(
                id = when (state.stepUp) {
                    AccountAuthorityStepUp.Oppose -> R.string.screen_account_authority_pin_footer_oppose
                    else -> R.string.screen_account_authority_pin_footer_adopt
                }
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        PinBubbleField(
            code = state.pin,
            length = AccountAuthorityState.PIN_LENGTH,
            hasError = state.errorMessage != null,
            enabled = !state.isWorking,
            masked = true,
            onValueChange = { eventSink(AccountAuthorityEvent.PinChanged(it)) },
            modifier = Modifier.padding(vertical = 24.dp),
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
            onClick = { eventSink(AccountAuthorityEvent.Submit) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun Explanation(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun formatTime(epochSeconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochSeconds * 1000))
