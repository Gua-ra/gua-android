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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.guaresolver.authority.AuthorityApproval
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.guaresolver.authority.AuthorityDevice
import io.element.android.libraries.guaresolver.authority.AuthorityFingerprint
import io.element.android.libraries.ui.strings.CommonStrings
import java.text.DateFormat
import java.util.Date

/**
 * GUA FORK: the account authority screen (ADM-009).
 *
 * Four rules shape what is drawn here. A pending transition is shown with the time it completes, because the
 * window IS the security of the transition and a screen that hid it would be hiding the only thing the owner
 * can act on. A quarantined device is shown as quarantined, because it can do nothing and counts for nothing.
 * The recovery artifact is shown on a screen of its own with the consequence spelled out, once, before
 * anything is submitted. And an account that has lost its authority is told that plainly, with no button that
 * pretends otherwise.
 */
@Composable
fun AccountAuthorityView(
    state: AccountAuthorityState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWebStepUpUrl: (String) -> Unit = {},
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
    // GUA FORK: the step-up of an account that holds a passkey runs on the page identity-service serves, so
    // once the presenter has the one-time URL it is handed to a Custom Tab and forgotten: the URL is single use,
    // and re-entering this screen must not reopen a sheet whose proof was already spent.
    val currentOnOpenWebStepUpUrl by rememberUpdatedState(onOpenWebStepUpUrl)
    LaunchedEffect(state.webStepUpUrl) {
        state.webStepUpUrl?.let { url ->
            currentOnOpenWebStepUpUrl(url)
            eventSink(AccountAuthorityEvent.ClearWebStepUpUrl)
        }
    }

    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (state.phase == AccountAuthorityPhase.Overview || state.phase == AccountAuthorityPhase.Loading) {
                onBackClick()
            } else {
                eventSink(AccountAuthorityEvent.Cancel)
            }
        },
        title = stringResource(id = R.string.screen_account_authority_title),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        when (state.phase) {
            AccountAuthorityPhase.Loading -> AsyncLoading()
            AccountAuthorityPhase.Artifact -> ArtifactSection(state = state, eventSink = eventSink)
            AccountAuthorityPhase.RecoveryEntry -> RecoveryEntrySection(state = state, eventSink = eventSink)
            AccountAuthorityPhase.AccountRecoveryNotice ->
                AccountRecoveryNoticeSection(state = state, eventSink = eventSink)
            AccountAuthorityPhase.Compare -> CompareSection(state = state, eventSink = eventSink)
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

        if (state.candidates.isNotEmpty()) {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_candidates_header)) },
                supportingContent = {
                    Text(stringResource(id = R.string.screen_account_authority_candidates_message))
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.DevicePasskey())),
            )
            state.candidates.forEach { candidate ->
                CandidateRow(candidate = candidate, enabled = !state.isWorking, eventSink = eventSink)
            }
            HorizontalDivider()
        }

        when {
            state.authorityLost -> {
                // Terminal by decision 7. The account keeps its id, its login and its data; what it never
                // regains is authority, and no copy here suggests a second adoption would work.
                Explanation(text = stringResource(id = R.string.screen_account_authority_lost_message))
            }
            state.canAdopt -> {
                Explanation(text = stringResource(id = R.string.screen_account_authority_bootstrap_message))
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.screen_account_authority_adopt_action)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
                    style = ListItemStyle.Primary,
                    onClick = { eventSink(AccountAuthorityEvent.StartAdoption) },
                )
            }
            state.devices.isNotEmpty() -> {
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.screen_account_authority_devices_header)) },
                    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Devices())),
                )
                state.devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        isThisDevice = device.deviceKeyB64Url == state.thisDeviceKeyB64Url,
                        canRevoke = state.deviceHoldsAuthority && !device.isRevoked && !state.isWorking,
                        eventSink = eventSink,
                    )
                }
            }
        }

        if (state.canOfferThisDevice) {
            // The other end of the candidate step: this phone has account access and no authority, so it
            // offers its own key and another device grants it.
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_offer_action)) },
                supportingContent = {
                    Text(
                        state.thisDeviceFingerprint?.let { fingerprint ->
                            stringResource(id = R.string.screen_account_authority_offer_fingerprint, fingerprint)
                        } ?: stringResource(id = R.string.screen_account_authority_offer_message)
                    )
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Share())),
                onClick = { eventSink(AccountAuthorityEvent.OfferThisDevice) },
            )
        }

        if (state.canRecover) {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.screen_account_authority_recover_action)) },
                supportingContent = {
                    Text(stringResource(id = R.string.screen_account_authority_recover_message))
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.KeySolid())),
                onClick = { eventSink(AccountAuthorityEvent.StartRecovery) },
            )
        }

        if (state.canRecoverThroughAccountRecovery) {
            // The other route, offered second and described as the weaker one, because that is what it is: any
            // device that still holds this account's authority can stop it while it waits.
            ListItem(
                headlineContent = {
                    Text(stringResource(id = R.string.screen_account_authority_account_recovery_action))
                },
                supportingContent = {
                    Text(stringResource(id = R.string.screen_account_authority_account_recovery_message))
                },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Restart())),
                onClick = { eventSink(AccountAuthorityEvent.StartAccountRecovery) },
            )
        }

        state.errorMessage?.let { message -> ErrorText(message) }
    }
}

/**
 * One device the chain has activated, in the chain's own terms.
 *
 * A quarantined device says so and says until when. Drawing it as "active" would be the screen telling the
 * owner they hold two devices when, for every rule that matters, they hold one.
 */
@Composable
private fun DeviceRow(
    device: AuthorityDevice,
    isThisDevice: Boolean,
    canRevoke: Boolean,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                buildString {
                    append(
                        device.label.ifEmpty {
                            stringResource(id = R.string.screen_account_authority_device_unlabelled)
                        }
                    )
                    if (isThisDevice) {
                        append(" ")
                        append(stringResource(id = R.string.screen_account_authority_device_this_phone))
                    }
                }
            )
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
        trailingContent = if (canRevoke) {
            ListItemContent.Custom { contentEnabled ->
                Button(
                    text = stringResource(
                        id = if (isThisDevice) {
                            R.string.screen_account_authority_revoke_self_action
                        } else {
                            R.string.screen_account_authority_revoke_action
                        }
                    ),
                    enabled = contentEnabled,
                    onClick = { eventSink(AccountAuthorityEvent.StartRevocation(device.deviceKeyB64Url)) },
                )
            }
        } else {
            null
        },
    )
}

/** A key another device offered. Nothing is signed from this row: it opens the comparison. */
@Composable
private fun CandidateRow(
    candidate: AuthorityCandidate,
    enabled: Boolean,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                candidate.label.ifEmpty {
                    stringResource(id = R.string.screen_account_authority_device_unlabelled)
                }
            )
        },
        supportingContent = { Text(AuthorityFingerprint.grouped(candidate.fingerprint)) },
        trailingContent = ListItemContent.Custom { contentEnabled ->
            Button(
                text = stringResource(id = R.string.screen_account_authority_candidate_action),
                enabled = enabled && contentEnabled,
                onClick = { eventSink(AccountAuthorityEvent.SelectCandidate(candidate.deviceKeyB64Url)) },
            )
        },
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
        // with a way into the account, can take it after a wait, and losing every device and this leaves
        // the account without authority for good.
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

/** Typing back the artifact a previous adoption or recovery handed over. */
@Composable
private fun RecoveryEntrySection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.screen_account_authority_recovery_entry_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_account_authority_recovery_entry_message),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        TextField(
            value = state.recoveryArtifactInput,
            onValueChange = { eventSink(AccountAuthorityEvent.RecoveryArtifactChanged(it)) },
            enabled = !state.isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
        state.recoveryArtifactError?.let { message -> ErrorText(message) }
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            enabled = state.canContinueFromRecoveryEntry,
            onClick = { eventSink(AccountAuthorityEvent.ContinueFromRecoveryEntry) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

/** The fingerprint comparison, which is the only thing binding a key to the person holding the other phone. */
@Composable
private fun CompareSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.screen_account_authority_compare_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(
                id = R.string.screen_account_authority_compare_message,
                state.selectedCandidate?.label.orEmpty(),
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Text(
            text = AuthorityFingerprint.grouped(state.selectedCandidate?.fingerprint.orEmpty()),
            style = ElementTheme.typography.fontHeadingMdBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(id = R.string.screen_account_authority_compare_warning),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
        )
        ListItem(
            headlineContent = { Text(stringResource(id = R.string.screen_account_authority_compare_confirm)) },
            trailingContent = ListItemContent.Switch(checked = state.fingerprintConfirmed),
            onClick = { eventSink(AccountAuthorityEvent.ConfirmFingerprint(!state.fingerprintConfirmed)) },
        )
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            enabled = state.canContinueFromCompare,
            onClick = { eventSink(AccountAuthorityEvent.ContinueFromCompare) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

/**
 * What the account-recovery route costs, before anything is minted (authorization 0x02).
 *
 * The acknowledgement is the gate, for the same reason the artifact's is: this route is authorized by the
 * account's own credentials rather than by a key, so the one thing that keeps it from being a takeover is the
 * wait and the veto every remaining device holds, and the owner has to know both before starting it.
 */
@Composable
private fun AccountRecoveryNoticeSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.screen_account_authority_account_recovery_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_account_authority_account_recovery_notice),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Text(
            text = stringResource(id = R.string.screen_account_authority_account_recovery_warning),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        ListItem(
            headlineContent = {
                Text(stringResource(id = R.string.screen_account_authority_account_recovery_confirm))
            },
            trailingContent = ListItemContent.Switch(checked = state.accountRecoveryAcknowledged),
            onClick = {
                eventSink(
                    AccountAuthorityEvent.AcknowledgeAccountRecovery(!state.accountRecoveryAcknowledged)
                )
            },
        )
        state.errorMessage?.let { message -> ErrorText(message) }
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            enabled = state.canContinueFromAccountRecoveryNotice,
            onClick = { eventSink(AccountAuthorityEvent.ContinueFromAccountRecoveryNotice) },
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
        state.stepUpBlock?.let { block ->
            // No PIN field, and no suggestion to add one. There is one case left here, an account holding
            // neither factor, and the way out of it is two-step verification rather than anything this screen
            // can offer.
            Text(
                text = stringResource(id = R.string.screen_account_authority_pin_header),
                style = ElementTheme.typography.fontHeadingSmMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Explanation(
                text = stringResource(
                    id = when (block) {
                        AccountAuthorityStepUpBlock.NoFactorRegistered ->
                            R.string.screen_account_authority_step_up_none
                        AccountAuthorityStepUpBlock.PasskeyNotUsableForObjection ->
                            R.string.screen_account_authority_step_up_objection_passkey
                    }
                )
            )
            return@Column
        }

        if (state.stepUpMethod == AccountAuthorityStepUpMethod.WebSheet) {
            WebStepUpSection(state = state, eventSink = eventSink)
            return@Column
        }

        Text(
            text = stringResource(id = R.string.screen_account_authority_pin_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = when (state.stepUp) {
                AccountAuthorityStepUp.Oppose ->
                    stringResource(id = R.string.screen_account_authority_pin_footer_oppose)
                AccountAuthorityStepUp.Grant ->
                    stringResource(id = R.string.screen_account_authority_pin_footer_grant)
                AccountAuthorityStepUp.Recover ->
                    stringResource(id = R.string.screen_account_authority_pin_footer_recover)
                AccountAuthorityStepUp.Revoke -> revocationFooter(state)
                else -> stringResource(id = R.string.screen_account_authority_pin_footer_adopt)
            },
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
        state.errorMessage?.let { message -> ErrorText(message) }
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

/**
 * The passkey arm, which runs in a Custom Tab because that is where an assertion can run on this platform.
 *
 * The copy says where the confirmation happens and that the passkey is what it asks for, and it does not offer
 * a PIN: an account holding a passkey already has the stronger of the two factors, and the page itself offers
 * the PIN to whoever also has one. Nothing comes back through this screen, so there is no field here and no
 * code to paste: the app asks the server again once it is in front.
 */
@Composable
private fun WebStepUpSection(
    state: AccountAuthorityState,
    eventSink: (AccountAuthorityEvent) -> Unit,
) {
    Column {
        Text(
            text = stringResource(id = R.string.screen_account_authority_pin_header),
            style = ElementTheme.typography.fontHeadingSmMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(
                id = if (state.awaitingWebStepUp) {
                    R.string.screen_account_authority_web_step_up_waiting
                } else {
                    R.string.screen_account_authority_web_step_up_message
                }
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        state.errorMessage?.let { message -> ErrorText(message) }
        Button(
            text = stringResource(id = R.string.screen_account_authority_web_step_up_action),
            enabled = state.canConfirmInBrowser,
            onClick = { eventSink(AccountAuthorityEvent.ConfirmInBrowser) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

/**
 * What a revocation about to be signed actually does, which is three different things.
 *
 * Removing this device takes effect at once. Removing another one waits out the window and is notified. And on
 * an account with two active devices the one being removed may object, which is decision 5's carve-out and a
 * standoff the owner should know about before starting it.
 */
@Composable
private fun revocationFooter(state: AccountAuthorityState): String {
    val target = state.revocationTarget ?: return stringResource(
        id = R.string.screen_account_authority_pin_footer_revoke_other
    )
    return when {
        target.isThisDevice -> stringResource(id = R.string.screen_account_authority_pin_footer_revoke_self)
        target.targetMayObject -> stringResource(
            id = R.string.screen_account_authority_pin_footer_revoke_two_devices,
            target.label,
        )
        else -> stringResource(id = R.string.screen_account_authority_pin_footer_revoke_other)
    }
}

@Composable
private fun ErrorText(message: Int) {
    Text(
        text = stringResource(id = message),
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
