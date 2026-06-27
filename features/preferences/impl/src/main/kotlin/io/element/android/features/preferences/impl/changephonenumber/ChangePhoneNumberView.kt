/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.changephonenumber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.components.PinBubbleField
import io.element.android.libraries.designsystem.components.async.AsyncLoading
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.phonenumberentry.Country
import io.element.android.libraries.ui.strings.CommonStrings

private val FieldShape = RoundedCornerShape(12.dp)
private val FieldHeight = 56.dp
private val CardShape = RoundedCornerShape(16.dp)
private val BadgeShape = RoundedCornerShape(16.dp)
private val BadgeSize = 56.dp

@Composable
fun ChangePhoneNumberView(
    state: ChangePhoneNumberState,
    onBackClick: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink

    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (state.phase.isEnteringFlow()) {
                eventSink(ChangePhoneNumberEvents.CancelEntry)
            } else {
                onBackClick()
            }
        },
        title = stringResource(id = state.phase.titleRes()),
    ) {
        when (state.phase) {
            ChangePhoneNumberPhase.Intro -> IntroSection(eventSink = eventSink)
            ChangePhoneNumberPhase.NeedsPinSetup -> NeedsPinSetupSection(eventSink = eventSink)
            ChangePhoneNumberPhase.Cooldown -> CooldownSection(state = state)
            ChangePhoneNumberPhase.EnteringNewPhone -> PhoneEntrySection(state = state, eventSink = eventSink)
            ChangePhoneNumberPhase.EnteringPin,
            ChangePhoneNumberPhase.EnteringOtp -> CodeEntrySection(state = state, eventSink = eventSink)
            ChangePhoneNumberPhase.Submitting -> AsyncLoading()
            ChangePhoneNumberPhase.Done -> DoneSection(eventSink = eventSink, onFinished = onFinished)
        }
    }
}

/**
 * GUA FORK: a first-class "hero" message card mirroring the iOS `ChangePhoneScreen` ListRow look — an
 * icon inside a rounded tinted badge above a clear heading + body, wrapped in a `bgSubtleSecondary`
 * card. Reused by the Intro, Done, no-PIN and cooldown screens so they all read as one design.
 */
@Composable
private fun MessageCard(
    icon: ImageVector,
    iconTint: Color,
    badgeColor: Color,
    heading: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(BadgeSize)
                .clip(BadgeShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = heading,
            style = ElementTheme.typography.fontHeadingSmMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = body,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun IntroSection(
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        MessageCard(
            icon = CompoundIcons.UserProfileSolid(),
            iconTint = ElementTheme.colors.iconPrimary,
            badgeColor = ElementTheme.colors.bgSubtlePrimary,
            heading = stringResource(id = R.string.screen_change_phone_intro_header),
            body = stringResource(id = R.string.screen_change_phone_intro_message),
        )
        Button(
            text = stringResource(id = CommonStrings.action_continue),
            onClick = { eventSink(ChangePhoneNumberEvents.Continue) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun NeedsPinSetupSection(
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        MessageCard(
            icon = CompoundIcons.LockSolid(),
            iconTint = ElementTheme.colors.iconPrimary,
            badgeColor = ElementTheme.colors.bgSubtlePrimary,
            heading = stringResource(id = R.string.screen_change_phone_needs_pin_header),
            body = stringResource(id = R.string.screen_change_phone_needs_pin_message),
        )
        Button(
            text = stringResource(id = R.string.screen_change_phone_needs_pin_action),
            onClick = { eventSink(ChangePhoneNumberEvents.SetUpPin) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun CooldownSection(
    state: ChangePhoneNumberState,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        MessageCard(
            icon = CompoundIcons.Time(),
            iconTint = ElementTheme.colors.iconCriticalPrimary,
            badgeColor = ElementTheme.colors.bgSubtlePrimary,
            heading = stringResource(id = R.string.screen_change_phone_cooldown_header),
            body = stringResource(
                id = R.string.screen_change_phone_cooldown_message,
                humanizeDuration(state.cooldownRemainingSeconds),
            ),
        )
    }
}

@Composable
private fun PhoneEntrySection(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Header label above the whole row, reading over both the selector and the field.
        Text(
            text = stringResource(id = R.string.screen_change_phone_new_header),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountrySelectorButton(
                country = state.selectedCountry,
                enabled = !state.isWorking,
                onClick = { eventSink(ChangePhoneNumberEvents.SelectCountry) },
            )
            Spacer(modifier = Modifier.width(10.dp))
            PhoneInput(
                value = state.localPhoneNumber,
                onValueChange = { eventSink(ChangePhoneNumberEvents.PhoneChanged(it)) },
                placeholder = state.selectedCountry.nationalExample,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            )
        }
        FooterOrError(
            state = state,
            footerRes = R.string.screen_change_phone_new_footer,
        )
        ContinueButton(state = state, eventSink = eventSink)
    }
}

/**
 * The country pill: flag + "+"+dialCode + chevron in a rounded `bgSubtleSecondary` surface, beside
 * the phone field. Settings-context tokens (NOT the aurora white used on the welcome screen).
 */
@Composable
private fun CountrySelectorButton(
    country: Country,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(FieldHeight)
            .clip(FieldShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = country.flag,
            style = ElementTheme.typography.fontHeadingMdBold,
        )
        Text(
            text = "+" + country.dialCode,
            modifier = Modifier.padding(start = 8.dp),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
        )
        Icon(
            imageVector = CompoundIcons.ChevronDown(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

/** A rounded `bgSubtleSecondary` phone field matching the selector pill. */
@Composable
private fun PhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = ElementTheme.typography.fontBodyLgRegular.copy(color = ElementTheme.colors.textPrimary),
        cursorBrush = SolidColor(ElementTheme.colors.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = modifier
            .height(FieldHeight)
            .clip(FieldShape)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CodeEntrySection(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        PinBubbleField(
            code = state.code,
            length = ChangePhoneNumberState.CODE_LENGTH,
            hasError = state.errorMessage != null,
            enabled = !state.isWorking,
            onValueChange = { eventSink(ChangePhoneNumberEvents.CodeChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
        FooterOrError(
            state = state,
            footerRes = state.phase.footerRes(),
        )
        ContinueButton(state = state, eventSink = eventSink)
    }
}

@Composable
private fun DoneSection(
    eventSink: (ChangePhoneNumberEvents) -> Unit,
    onFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        MessageCard(
            icon = CompoundIcons.CheckCircleSolid(),
            iconTint = ElementTheme.colors.iconSuccessPrimary,
            badgeColor = ElementTheme.colors.bgSubtlePrimary,
            heading = stringResource(id = R.string.screen_change_phone_done_header),
            body = stringResource(id = R.string.screen_change_phone_done_message),
        )
        Button(
            text = stringResource(id = CommonStrings.action_done),
            onClick = {
                eventSink(ChangePhoneNumberEvents.Done)
                onFinished()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun FooterOrError(
    state: ChangePhoneNumberState,
    footerRes: Int?,
) {
    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        Text(
            text = stringResource(id = errorMessage),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else if (footerRes != null) {
        Text(
            text = stringResource(id = footerRes),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ContinueButton(
    state: ChangePhoneNumberState,
    eventSink: (ChangePhoneNumberEvents) -> Unit,
) {
    Button(
        text = stringResource(
            id = if (state.isWorking) CommonStrings.common_loading else CommonStrings.action_continue
        ),
        onClick = { eventSink(ChangePhoneNumberEvents.Continue) },
        enabled = state.canContinue,
        showProgress = state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    )
}

/**
 * GUA FORK: turns a remaining-cooldown second count into a short human phrase for the Cooldown
 * interstitial, e.g. "6 days, 3 hours", "5 hours", "1 minute". Keeps the largest two non-zero units
 * and never renders "0 minutes" (falls back to "a moment"). English copy lives here since these are
 * temporary fork strings; humanisation mirrors the iOS view model.
 */
internal fun humanizeDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "a moment"
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60

    fun unit(value: Long, singular: String) = "$value $singular${if (value == 1L) "" else "s"}"

    val parts = buildList {
        if (days > 0) add(unit(days, "day"))
        if (hours > 0) add(unit(hours, "hour"))
        // Only show minutes when they add precision and we are not already showing days.
        if (minutes > 0 && days == 0L) add(unit(minutes, "minute"))
    }
    return when {
        parts.isEmpty() -> "a moment"
        else -> parts.take(2).joinToString(", ")
    }
}

private fun ChangePhoneNumberPhase.isEnteringFlow(): Boolean = when (this) {
    ChangePhoneNumberPhase.EnteringNewPhone,
    ChangePhoneNumberPhase.EnteringPin,
    ChangePhoneNumberPhase.EnteringOtp,
    ChangePhoneNumberPhase.Submitting -> true
    else -> false
}

private fun ChangePhoneNumberPhase.titleRes(): Int = when (this) {
    ChangePhoneNumberPhase.Intro,
    ChangePhoneNumberPhase.NeedsPinSetup,
    ChangePhoneNumberPhase.Cooldown,
    ChangePhoneNumberPhase.Submitting -> R.string.screen_change_phone_title
    ChangePhoneNumberPhase.EnteringNewPhone -> R.string.screen_change_phone_new_header
    ChangePhoneNumberPhase.EnteringPin -> R.string.screen_change_phone_pin_header
    ChangePhoneNumberPhase.EnteringOtp -> R.string.screen_change_phone_otp_header
    ChangePhoneNumberPhase.Done -> R.string.screen_change_phone_done_header
}

private fun ChangePhoneNumberPhase.footerRes(): Int? = when (this) {
    ChangePhoneNumberPhase.EnteringNewPhone -> R.string.screen_change_phone_new_footer
    ChangePhoneNumberPhase.EnteringPin -> R.string.screen_change_phone_pin_footer
    ChangePhoneNumberPhase.EnteringOtp -> R.string.screen_change_phone_otp_footer
    else -> null
}

@PreviewsDayNight
@Composable
internal fun ChangePhoneNumberViewPreview(
    @PreviewParameter(ChangePhoneNumberStateProvider::class) state: ChangePhoneNumberState,
) = ElementPreview {
    ChangePhoneNumberView(
        state = state,
        onBackClick = {},
        onFinished = {},
    )
}
