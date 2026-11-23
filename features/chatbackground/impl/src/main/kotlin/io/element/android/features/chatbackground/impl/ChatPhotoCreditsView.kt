/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.chatbackground.impl

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.chatbackground.api.ChatArtwork
import io.element.android.libraries.chatbackground.api.ChatBackground
import io.element.android.libraries.chatbackground.api.ChatPhotoCredit
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.ui.chatbackground.ChatBackgroundCanvas

private val THUMBNAIL_SHAPE = RoundedCornerShape(8.dp)

@Composable
internal fun photoCreditText(credit: ChatPhotoCredit, withLicence: Boolean): String = if (withLicence) {
    stringResource(R.string.gua_chat_theme_photo_credit_licence, credit.author, credit.licence.displayName)
} else {
    stringResource(R.string.gua_chat_theme_photo_credit, credit.author)
}

/** Never disabled, so members who cannot change the theme still reach the credits. */
@Composable
internal fun PhotoCreditLine(
    credit: ChatPhotoCredit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = photoCreditText(credit, withLicence = true),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun PhotoCreditsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        text = stringResource(R.string.gua_chat_theme_photo_credits),
        onClick = onClick,
        size = ButtonSize.Small,
        modifier = modifier,
    )
}

@Composable
internal fun ChatPhotoCreditsView(
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.gua_chat_theme_photo_credits),
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(ChatArtwork.photos) { photo ->
                PhotoCreditRow(photo = photo, credit = requireNotNull(photo.credit), onOpenUrl = onOpenUrl)
            }
        }
    }
}

@Composable
private fun PhotoCreditRow(
    photo: ChatArtwork,
    credit: ChatPhotoCredit,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(9f / 16f)
                .clip(THUMBNAIL_SHAPE)
                .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, THUMBNAIL_SHAPE),
        ) {
            ChatBackgroundCanvas(background = ChatBackground.Art(photo), modifier = Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(photo.nameRes),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = photoCreditText(credit, withLicence = false),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LinkText(text = credit.licence.displayName, url = credit.licence.deedUrl, onOpenUrl = onOpenUrl)
                credit.sourceUrl?.let { url ->
                    LinkText(text = stringResource(R.string.gua_chat_theme_photo_source), url = url, onOpenUrl = onOpenUrl)
                }
            }
            Text(
                text = stringResource(R.string.gua_chat_theme_photo_changes),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LinkText(
    text: String,
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    val styles = TextLinkStyles(SpanStyle(color = ElementTheme.colors.textLinkExternal, textDecoration = TextDecoration.Underline))
    Text(
        text = buildAnnotatedString {
            withLink(LinkAnnotation.Url(url = url, styles = styles, linkInteractionListener = { onOpenUrl(url) })) {
                append(text)
            }
        },
        style = ElementTheme.typography.fontBodyMdRegular,
    )
}
