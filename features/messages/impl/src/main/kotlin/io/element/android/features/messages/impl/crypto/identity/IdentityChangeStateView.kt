/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.crypto.identity

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.appconfig.LearnMoreConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertLevel
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertMolecule
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.api.encryption.identity.isAViolation
import io.element.android.libraries.matrix.ui.room.RoomMemberIdentityStateChange
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun IdentityChangeStateView(
    state: IdentityChangeState,
    onLinkClick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pick the first identity change that is a violation
    val identityChangeViolation = state.roomMemberIdentityStateChanges.firstOrNull {
        it.identityState.isAViolation()
    }
    when (identityChangeViolation?.identityState) {
        // GUA FORK: both violations read as one calm, informational notice with a single
        // acknowledging action. The action still differs underneath (pin the new identity, or
        // withdraw a stale verification) so sending works immediately after the tap, but a
        // contact reinstalling Gua is not an alarm and is never styled as critical.
        // Matches gua-ios RoomScreenFooterView.
        IdentityState.PinViolation -> ViolationAlert(
            identityChangeViolation = identityChangeViolation,
            onLinkClick = onLinkClick,
            textId = R.string.gua_identity_change_banner,
            isCritical = false,
            submitTextId = CommonStrings.action_ok,
            onSubmitClick = { state.eventSink(IdentityChangeEvent.PinIdentity(identityChangeViolation.identityRoomMember.userId)) },
            modifier = modifier,
        )
        IdentityState.VerificationViolation -> ViolationAlert(
            identityChangeViolation = identityChangeViolation,
            onLinkClick = onLinkClick,
            textId = R.string.gua_identity_change_banner,
            isCritical = false,
            submitTextId = CommonStrings.action_ok,
            onSubmitClick = { state.eventSink(IdentityChangeEvent.WithdrawVerification(identityChangeViolation.identityRoomMember.userId)) },
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun ViolationAlert(
    identityChangeViolation: RoomMemberIdentityStateChange,
    onLinkClick: (String, Boolean) -> Unit,
    @StringRes textId: Int,
    isCritical: Boolean,
    @StringRes submitTextId: Int,
    onSubmitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerAlertMolecule(
        modifier = modifier,
        avatar = identityChangeViolation.identityRoomMember.avatarData,
        content = buildAnnotatedString {
            // GUA FORK: the handle is deliberately absent. Upstream printed the full user id
            // in bold next to the name; Gua never surfaces one in conversation copy, and the
            // display name is what identifies the contact to the reader.
            val learnMoreStr = stringResource(CommonStrings.action_learn_more)
            val displayName = identityChangeViolation.identityRoomMember.displayNameOrDefault
            val fullText = stringResource(textId, displayName, learnMoreStr)
            append(fullText)
            val displayNameStartIndex = fullText.indexOf(displayName)
            if (displayNameStartIndex >= 0) {
                addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                    ),
                    start = displayNameStartIndex,
                    end = displayNameStartIndex + displayName.length,
                )
            }
            val learnMoreStartIndex = fullText.lastIndexOf(learnMoreStr)
            addStyle(
                style = SpanStyle(
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold,
                    color = ElementTheme.colors.textPrimary
                ),
                start = learnMoreStartIndex,
                end = learnMoreStartIndex + learnMoreStr.length,
            )
            addLink(
                url = LinkAnnotation.Url(
                    url = LearnMoreConfig.IDENTITY_CHANGE_URL,
                    linkInteractionListener = {
                        onLinkClick(LearnMoreConfig.IDENTITY_CHANGE_URL, true)
                    }
                ),
                start = learnMoreStartIndex,
                end = learnMoreStartIndex + learnMoreStr.length,
            )
        },
        submitText = stringResource(submitTextId),
        onSubmitClick = onSubmitClick,
        level = if (isCritical) ComposerAlertLevel.Critical else ComposerAlertLevel.Info,
    )
}

@PreviewsDayNight
@Composable
internal fun IdentityChangeStateViewPreview(
    @PreviewParameter(IdentityChangeStateProvider::class) state: IdentityChangeState,
) = ElementPreview {
    IdentityChangeStateView(
        state = state,
        onLinkClick = { _, _ -> },
    )
}
