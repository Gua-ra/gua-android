/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.login.LoginModeView
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.atoms.ElementLogoAtom
import io.element.android.libraries.designsystem.atomic.atoms.ElementLogoAtomSize
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.atomic.pages.OnBoardingPage
import io.element.android.libraries.designsystem.background.GuaWelcomeBackground
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

// Refs:
// FTUE:
// - https://www.figma.com/file/o9p34zmiuEpZRyvZXJZAYL/FTUE?type=design&node-id=133-5427&t=5SHVppfYzjvkEywR-0
// ElementX:
// - https://www.figma.com/file/0MMNu7cTOzLOlWb7ctTkv3/Element-X?type=design&node-id=1816-97419
@Composable
fun OnBoardingView(
    state: OnBoardingState,
    onBackClick: () -> Unit,
    onDeveloperSettingsClick: () -> Unit,
    onSignInWithQrCode: () -> Unit,
    onSignIn: (mustChooseAccountProvider: Boolean) -> Unit,
    onCreateAccount: () -> Unit,
    onOAuthDetails: (OAuthDetails) -> Unit,
    onNeedLoginPassword: () -> Unit,
    onLearnMoreClick: () -> Unit,
    onCreateAccountContinue: (url: String) -> Unit,
    onReportProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loginView = @Composable {
        LoginModeView(
            loginMode = state.loginMode,
            onClearError = {
                state.eventSink(OnBoardingEvents.ClearError)
            },
            onLearnMoreClick = onLearnMoreClick,
            onOAuthDetails = onOAuthDetails,
            onNeedLoginPassword = onNeedLoginPassword,
            onCreateAccountContinue = onCreateAccountContinue,
        )
    }
    val buttons = @Composable {
        OnBoardingButtons(
            state = state,
            onSignInWithQrCode = onSignInWithQrCode,
            onSignIn = onSignIn,
            onCreateAccount = onCreateAccount,
            onReportProblem = onReportProblem,
        )
    }

    if (state.isAddingAccount) {
        AddOtherAccountScaffold(
            modifier = modifier,
            loginView = loginView,
            buttons = buttons,
            onBackClick = onBackClick,
        )
    } else {
        AddFirstAccountScaffold(
            modifier = modifier,
            state = state,
            loginView = loginView,
            buttons = buttons,
            onBackClick = onBackClick,
            onDeveloperSettingsClick = onDeveloperSettingsClick,
        )
    }
}

@Composable
private fun AddFirstAccountScaffold(
    state: OnBoardingState,
    loginView: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    onBackClick: () -> Unit,
    onDeveloperSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnBoardingPage(
        modifier = modifier,
        // We paint our own full-bleed Gua-green "aurora" (see GuaWelcomeBackground) rather than the
        // default Element onboarding image, for the branded welcome look that matches Gua iOS.
        background = { GuaWelcomeBackground() },
        content = {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.onBoardingLogoResId != null) {
                    OnBoardingLogo(
                        onBoardingLogoResId = state.onBoardingLogoResId,
                    )
                } else {
                    OnBoardingContent()
                }
                if (state.showDeveloperSettings) {
                    IconButton(
                        onClick = onDeveloperSettingsClick,
                        modifier = Modifier
                            .align(Alignment.TopStart),
                    ) {
                        Icon(
                            imageVector = CompoundIcons.SettingsSolid(),
                            contentDescription = stringResource(CommonStrings.common_developer_options),
                        )
                    }
                }
                if (state.showBackButton) {
                    // Add icon button to "navigate back"
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_cancel),
                        )
                    }
                }
            }
            loginView()
        },
        footer = {
            buttons()
        }
    )
}

@Composable
private fun AddOtherAccountScaffold(
    loginView: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowStepPage(
        modifier = modifier,
        title = stringResource(CommonStrings.common_add_account),
        iconStyle = BigIcon.Style.Default(CompoundIcons.HomeSolid()),
        buttons = { buttons() },
        content = loginView,
        onBackClick = onBackClick,
    )
}

/**
 * The branded Gua welcome content: the premium Gua logo, a centered title + subtitle, and a quiet
 * "end-to-end encrypted" trust pill. Sits over the [GuaWelcomeBackground] aurora, so text uses
 * light, brand-fixed colours (the aurora is dark green in both light and dark themes).
 */
@Composable
private fun OnBoardingContent() {
    // The aurora canvas is dark in both themes, so we use light text rather than theme tokens
    // (which would be near-black in light mode and disappear).
    val titleColor = Color.White
    val subtitleColor = Color.White.copy(alpha = 0.78f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = CenterHorizontally,
        ) {
            ElementLogoAtom(
                size = ElementLogoAtomSize.Large,
                // Force the lighter glass treatment so the logo reads as a premium object on the
                // dark aurora regardless of the active light/dark theme.
                darkTheme = false,
                modifier = Modifier.padding(top = ElementLogoAtomSize.Large.shadowRadius / 2),
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = stringResource(id = R.string.screen_onboarding_welcome_title),
                color = titleColor,
                style = ElementTheme.typography.fontHeadingLgBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(id = R.string.screen_onboarding_welcome_message),
                color = subtitleColor,
                style = ElementTheme.typography.fontBodyLgRegular,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            TrustEncryptedPill()
        }
    }
}

@Composable
private fun TrustEncryptedPill(
    modifier: Modifier = Modifier,
) {
    // A quiet translucent capsule that reads cleanly on the dark aurora.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Lock(),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(id = R.string.screen_onboarding_trust_encrypted),
            color = Color.White.copy(alpha = 0.85f),
            style = ElementTheme.typography.fontBodyMdMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnBoardingLogo(
    onBoardingLogoResId: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = onBoardingLogoResId),
            contentDescription = null
        )
    }
}

@Composable
private fun OnBoardingButtons(
    state: OnBoardingState,
    onSignInWithQrCode: () -> Unit,
    onSignIn: (mustChooseAccountProvider: Boolean) -> Unit,
    onCreateAccount: () -> Unit,
    onReportProblem: () -> Unit,
) {
    val isLoading by remember(state.loginMode) {
        derivedStateOf {
            state.loginMode is AsyncData.Loading
        }
    }

    ButtonColumnMolecule {
        val signInButtonStringRes = if (state.canLoginWithQrCode || state.canCreateAccount) {
            R.string.screen_onboarding_sign_in_manually
        } else {
            CommonStrings.action_continue
        }
        if (state.canLoginWithQrCode) {
            Button(
                text = stringResource(id = R.string.screen_onboarding_sign_in_with_qr_code),
                leadingIcon = IconSource.Vector(CompoundIcons.QrCode()),
                onClick = onSignInWithQrCode,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val defaultAccountProvider = state.defaultAccountProvider
        if (defaultAccountProvider == null) {
            Button(
                text = stringResource(id = signInButtonStringRes),
                onClick = {
                    onSignIn(state.mustChooseAccountProvider)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.onBoardingSignIn)
            )
        } else {
            Button(
                text = stringResource(id = R.string.screen_onboarding_sign_in_to, defaultAccountProvider),
                showProgress = isLoading,
                onClick = {
                    state.eventSink(OnBoardingEvents.OnSignIn(defaultAccountProvider))
                },
                enabled = state.submitEnabled || isLoading,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
        if (state.canCreateAccount) {
            TextButton(
                text = stringResource(id = R.string.screen_onboarding_sign_up),
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
        if (state.isAddingAccount.not()) {
            if (state.canReportBug) {
                // Add a report problem text button. Use a Text since we need a special theme here.
                Text(
                    modifier = Modifier
                        .clickable(onClick = onReportProblem)
                        .padding(16.dp),
                    text = stringResource(id = CommonStrings.common_report_a_problem),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            } else {
                Text(
                    modifier = Modifier
                        .clickable {
                            state.eventSink(OnBoardingEvents.OnVersionClick)
                        }
                        .padding(16.dp),
                    text = stringResource(id = R.string.screen_onboarding_app_version, state.version),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun OnBoardingViewPreview(
    @PreviewParameter(OnBoardingStateProvider::class) state: OnBoardingState
) = ElementPreview {
    OnBoardingView(
        state = state,
        onBackClick = {},
        onDeveloperSettingsClick = {},
        onSignInWithQrCode = {},
        onSignIn = {},
        onCreateAccount = {},
        onReportProblem = {},
        onOAuthDetails = {},
        onNeedLoginPassword = {},
        onLearnMoreClick = {},
        onCreateAccountContinue = {},
    )
}
