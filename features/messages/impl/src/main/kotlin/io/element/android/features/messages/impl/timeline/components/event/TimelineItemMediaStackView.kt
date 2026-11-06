/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.mediastack.LocalMediaStackPositions
import io.element.android.features.messages.impl.mediastack.hiddenMembers
import io.element.android.features.messages.impl.mediastack.showMembers
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.components.MessageEventBubbleDefaults
import io.element.android.features.messages.impl.timeline.components.TimelineEventTimestampView
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.MediaStackMember
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.blurHash
import io.element.android.features.messages.impl.timeline.protection.ProtectedView
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.features.messages.impl.timeline.util.handleAsyncImageStateChange
import io.element.android.libraries.androidutils.system.areAnimationsEnabled
import io.element.android.libraries.designsystem.components.blurhash.blurHashBackground
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.matrix.ui.media.contentvalidation.rememberEventContentValidationState
import io.element.android.libraries.matrixmedia.api.MAX_THUMBNAIL_HEIGHT
import io.element.android.libraries.matrixmedia.api.MAX_THUMBNAIL_WIDTH
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.time.formatShort
import io.element.android.wysiwyg.link.Link
import kotlin.math.abs
import kotlin.math.sign

private val CARD_WIDTH = 220.dp
private val CARD_HEIGHT = 275.dp
private val CARD_SHAPE = RoundedCornerShape(18.dp)
private val CARD_SHADOW_COLOR = Color.Black.copy(alpha = 0.12f)
private val FRAME_EXTRA_WIDTH = 12.dp
private val FRAME_EXTRA_HEIGHT = 8.dp
private val BEHIND_CARD_OFFSET_X = 6.dp
private val BEHIND_CARD_OFFSET_Y = 4.dp
private const val BEHIND_CARD_SCALE_STEP = 0.04f
private val BEHIND_CARD_ROTATIONS = floatArrayOf(3f, -2f)
private const val MAX_DRAG_ROTATION = 8f
private const val COMMIT_DISTANCE_FRACTION = 0.3f
private val COMMIT_VELOCITY_PER_SECOND = 500.dp
private const val THROWN_DISTANCE_FRACTION = 1.2f
private const val UNSENT_CARD_ALPHA = 0.6f
private val STACK_SPRING = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
internal val MediaStackOverlayColor = Color.Black.copy(alpha = 0.45f)

@Composable
fun TimelineItemMediaStackView(
    content: TimelineItemMediaStackContent,
    isMine: Boolean,
    gesturesEnabled: Boolean,
    timelineProtectionState: TimelineProtectionState,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    onActivate: (() -> Unit)?,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    timestamp: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions = LocalMediaStackPositions.current
    val frontIndex = positions.frontIndex(content)
    val header = mediaStackHeaderText(content.kind, content.members.size)
    val position = mediaStackPositionText(frontIndex, content.members.size, content.members[frontIndex].videoDuration())
    val caption = content.caption
    // Activating a stack whose media are hidden shows them, as the Show button over a single hidden photo does.
    val showLabel = stringResource(CommonStrings.action_show).takeIf { timelineProtectionState.hiddenMembers(content).isNotEmpty() }
    val timestampContent = remember(timestamp) { movableContentOf(timestamp) }
    Column(
        modifier = modifier,
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        MediaStackHeader(
            text = header,
            enabled = gesturesEnabled,
            onClick = { eventSink(TimelineEvent.OpenMediaStackGrid(content)) },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box {
            MediaStackCards(
                content = content,
                frontIndex = frontIndex,
                isMine = isMine,
                gesturesEnabled = gesturesEnabled,
                timelineProtectionState = timelineProtectionState,
                eventSink = eventSink,
                onFrontIndexChange = { positions.show(content, it) },
                modifier = Modifier.clearAndSetSemantics {
                    // TalkBack's default order speaks the state before the name, so the caption stays with the header, not after the position.
                    contentDescription = listOfNotNull(header, caption).joinToString(", ")
                    stateDescription = position
                    onActivate?.let { activate ->
                        onClick(label = showLabel) {
                            activate()
                            true
                        }
                    }
                },
            )
            if (caption == null) {
                timestampContent(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        if (caption != null) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(CARD_WIDTH)
                    .background(MessageEventBubbleDefaults.backgroundBubbleColor(isMine), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CaptionView(
                    caption = caption,
                    formattedCaption = content.formattedCaption,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                    onContentLayoutChange = {},
                    modifier = Modifier.clearAndSetSemantics {},
                )
                timestampContent(Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun MediaStackHeader(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp)
            // A screen reader opens the grid with the stack's Show all action instead.
            .clearAndSetSemantics { hideFromAccessibility() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Grid(),
            contentDescription = null,
            tint = ElementTheme.colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MediaStackCards(
    content: TimelineItemMediaStackContent,
    frontIndex: Int,
    isMine: Boolean,
    gesturesEnabled: Boolean,
    timelineProtectionState: TimelineProtectionState,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    onFrontIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 1 when the centre of the screen is to the right of the stack, -1 when it is to the left.
    val towardCentre = if (isMine != isRtl) -1f else 1f
    val cardWidthPx = with(density) { CARD_WIDTH.toPx() }
    val commitVelocityPx = with(density) { COMMIT_VELOCITY_PER_SECOND.toPx() }
    val context = LocalContext.current
    val animationsEnabled = remember { context.areAnimationsEnabled() }
    val haptics = LocalHapticFeedback.current
    val dragOffset = remember(content.stackId) { mutableFloatStateOf(0f) }
    val latestFrontIndex by rememberUpdatedState(frontIndex)
    val draggableState = rememberDraggableState { delta -> dragOffset.floatValue += delta }
    // Dragging toward the leading edge brings the next card, toward the trailing edge the previous one.
    val revealStep by remember(dragOffset, isRtl) { derivedStateOf { if (isTowardTrailing(dragOffset.floatValue, isRtl)) -1 else 1 } }
    val members = content.members
    val visibleCount = minOf(members.size, 1 + BEHIND_CARD_ROTATIONS.size)
    val hiddenMembers = timelineProtectionState.hiddenMembers(content)
    // Dark mode draws a hairline around each card instead.
    val hasShadow = ElementTheme.isLightTheme

    Box(
        modifier = modifier
            .size(CARD_WIDTH + FRAME_EXTRA_WIDTH, CARD_HEIGHT + FRAME_EXTRA_HEIGHT)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStopped = { velocity ->
                    val offset = dragOffset.floatValue
                    val direction = commitDirection(offset, velocity, cardWidthPx, commitVelocityPx)
                    if (direction == 0f) {
                        animate(offset, 0f, velocity, STACK_SPRING) { value, _ -> dragOffset.floatValue = value }
                    } else {
                        if (animationsEnabled) {
                            animate(offset, direction * cardWidthPx * THROWN_DISTANCE_FRACTION, velocity, STACK_SPRING) { value, _ ->
                                dragOffset.floatValue = value
                            }
                        }
                        onFrontIndexChange(latestFrontIndex + if (isTowardTrailing(direction, isRtl)) -1 else 1)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        dragOffset.floatValue = 0f
                    }
                },
            ),
        contentAlignment = if (isMine) Alignment.BottomEnd else Alignment.BottomStart,
    ) {
        for (depth in visibleCount - 1 downTo 0) {
            val member = members[(frontIndex + depth * revealStep).mod(members.size)]
            key(member.event.id) {
                MediaStackCard(
                    member = member,
                    hideContent = member.event in hiddenMembers,
                    onShowClick = { timelineProtectionState.showMembers(content) },
                    eventSink = eventSink,
                    modifier = Modifier.stackCardTransform(
                        depth = depth,
                        dragOffset = { dragOffset.floatValue },
                        cardWidthPx = cardWidthPx,
                        towardCentre = towardCentre,
                        followsDrag = animationsEnabled,
                        hasShadow = hasShadow,
                    ),
                )
            }
        }
    }
}

private fun isTowardTrailing(offset: Float, isRtl: Boolean) = if (isRtl) offset < 0 else offset > 0

// The sign of the drag when the release commits, past 30% of a card or flung the same way fast enough, else 0 to spring back.
private fun commitDirection(offset: Float, velocity: Float, cardWidthPx: Float, commitVelocityPx: Float): Float {
    val isPastDistance = abs(offset) > cardWidthPx * COMMIT_DISTANCE_FRACTION
    val isFlungAlong = abs(velocity) > commitVelocityPx && velocity * offset > 0
    return if (isPastDistance || isFlungAlong) sign(offset) else 0f
}

private fun Modifier.stackCardTransform(
    depth: Int,
    dragOffset: () -> Float,
    cardWidthPx: Float,
    towardCentre: Float,
    followsDrag: Boolean,
    hasShadow: Boolean,
) = graphicsLayer {
    val offset = dragOffset()
    if (depth == 0) {
        if (followsDrag) {
            translationX = offset
            rotationZ = MAX_DRAG_ROTATION * (offset / cardWidthPx).coerceIn(-1f, 1f)
        }
    } else {
        val restingScale = 1f - BEHIND_CARD_SCALE_STEP * depth
        // The card the drag reveals grows to full size as the front card leaves; without animations every card stays in place.
        val scale = if (depth == 1 && followsDrag) restingScale + (1f - restingScale) * (abs(offset) / cardWidthPx).coerceAtMost(1f) else restingScale
        translationX = towardCentre * (BEHIND_CARD_OFFSET_X * depth).toPx()
        translationY = -(BEHIND_CARD_OFFSET_Y * depth).toPx()
        rotationZ = towardCentre * BEHIND_CARD_ROTATIONS[depth - 1]
        scaleX = scale
        scaleY = scale
    }
    shadowElevation = if (hasShadow) 3.dp.toPx() else 0f
    ambientShadowColor = CARD_SHADOW_COLOR
    spotShadowColor = CARD_SHADOW_COLOR
    shape = CARD_SHAPE
    clip = true
}

@Composable
private fun MediaStackCard(
    member: MediaStackMember,
    hideContent: Boolean,
    onShowClick: () -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = member.event
    val validationState = rememberEventContentValidationState(eventId = event.eventId, needsValidation = true)
    val validation by validationState.collectOverallState()
    val updatedEventSink by rememberUpdatedState(eventSink)
    LaunchedEffect(event.eventId, member.media) {
        val eventId = event.eventId ?: return@LaunchedEffect
        updatedEventSink(TimelineEvent.ValidateMedia(eventId, listOfNotNull(member.thumbnailSource, member.media.mediaSource), validationState))
    }
    val isUnsent = event.localSendState is LocalEventSendState.Sending
    val darkBorder = if (ElementTheme.isLightTheme) Modifier else Modifier.border(0.5.dp, ElementTheme.colors.borderInteractiveSecondary, CARD_SHAPE)
    Box(
        modifier = modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .background(ElementTheme.colors.bgSubtleSecondary)
            .blurHashBackground(member.media.blurHash(), alpha = 0.9f)
            .then(darkBorder),
        contentAlignment = Alignment.Center,
    ) {
        when {
            validation.hasError() -> MediaStackScanFailure()
            !validation.isValidated() -> CircularProgressIndicator()
            else -> ProtectedView(hideContent = hideContent, onShowClick = onShowClick) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isUnsent) Modifier.alpha(UNSENT_CARD_ALPHA) else Modifier),
                ) {
                    val still = member.stillSource?.let(::mediaStackStill)
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = still,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        contentDescription = null,
                        onState = { state ->
                            val url = still?.source?.safeUrl ?: return@AsyncImage
                            handleAsyncImageStateChange(
                                state = state,
                                onLoaded = {},
                                updateContentValidationState = { validationState.update(url, it) },
                            )
                        },
                    )
                    if (member is MediaStackMember.Video) {
                        VideoCardOverlay(member.media)
                    }
                }
            }
        }
        // At the top, as the timestamp covers the bottom corner of the front card.
        MediaStackCardSendState(
            member = event,
            eventSink = eventSink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}

@Composable
internal fun MediaStackScanFailure(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCriticalSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Error(),
            contentDescription = null,
            tint = ElementTheme.colors.iconCriticalPrimary,
        )
    }
}

@Composable
private fun VideoCardOverlay(video: TimelineItemVideoContent) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .background(MediaStackOverlayColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.PlaySolid(),
                contentDescription = null,
                tint = Color.White,
            )
        }
        Text(
            text = video.duration.formatShort(),
            style = ElementTheme.typography.fontBodyXsMedium.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(MediaStackOverlayColor, CircleShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MediaStackCardSendState(
    member: TimelineItem.Event,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressModifier = modifier
        .background(MediaStackOverlayColor, CircleShape)
        .size(20.dp)
    when (val sendState = member.localSendState) {
        is LocalEventSendState.Sending.MediaWithProgress -> CircularProgressIndicator(
            progress = { if (sendState.total > 0) sendState.progress.toFloat() / sendState.total else 0f },
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = progressModifier,
        )
        is LocalEventSendState.Sending -> CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = progressModifier,
        )
        is LocalEventSendState.Failed -> Icon(
            imageVector = CompoundIcons.ErrorSolid(),
            contentDescription = stringResource(CommonStrings.common_sending_failed),
            tint = ElementTheme.colors.iconCriticalPrimary,
            modifier = modifier
                .background(ElementTheme.colors.bgCanvasDefault, CircleShape)
                .clip(CircleShape)
                .clickable { eventSink(TimelineEvent.ShowSendFailureDialog(member)) }
                .size(20.dp),
        )
        is LocalEventSendState.Sent,
        null -> Unit
    }
}

internal fun mediaStackStill(source: MediaSource) = MediaRequestData(
    source = source,
    kind = MediaRequestData.Kind.Thumbnail(width = MAX_THUMBNAIL_WIDTH, height = MAX_THUMBNAIL_HEIGHT),
)

internal fun MediaStackMember.videoDuration(): String? = when (this) {
    is MediaStackMember.Photo -> null
    is MediaStackMember.Video -> media.duration.formatShort()
}

@Composable
internal fun mediaStackHeaderText(kind: TimelineItemMediaStackContent.Kind, count: Int): String = pluralStringResource(kind.headerPlural, count, count)

@Composable
internal fun mediaStackPositionText(index: Int, count: Int, videoDuration: String?): String = if (videoDuration != null) {
    stringResource(R.string.gua_media_stack_a11y_video_position, index + 1, count, videoDuration)
} else {
    stringResource(R.string.gua_media_stack_a11y_photo_position, index + 1, count)
}

// The swipe and the header tap are out of a screen reader's reach.
@Composable
internal fun rememberMediaStackAccessibilityActions(
    content: TimelineItemMediaStackContent,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
): List<CustomAccessibilityAction> {
    val positions = LocalMediaStackPositions.current
    val nextLabel = stringResource(R.string.gua_media_stack_a11y_next)
    val previousLabel = stringResource(R.string.gua_media_stack_a11y_previous)
    val showAllLabel = stringResource(R.string.gua_media_stack_show_all)
    val latestContent by rememberUpdatedState(content)
    val latestEventSink by rememberUpdatedState(eventSink)
    return remember(positions, nextLabel, previousLabel, showAllLabel) {
        listOf(
            CustomAccessibilityAction(nextLabel) {
                positions.show(latestContent, positions.frontIndex(latestContent) + 1)
                true
            },
            CustomAccessibilityAction(previousLabel) {
                positions.show(latestContent, positions.frontIndex(latestContent) - 1)
                true
            },
            CustomAccessibilityAction(showAllLabel) {
                latestEventSink(TimelineEvent.OpenMediaStackGrid(latestContent))
                true
            },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemMediaStackViewPreview(
    @PreviewParameter(TimelineItemMediaStackContentPreviewParam::class) content: TimelineItemMediaStackContent,
) = ElementPreview {
    TimelineItemMediaStackView(
        content = content,
        isMine = content.first.isMine,
        gesturesEnabled = true,
        timelineProtectionState = aTimelineProtectionState(),
        eventSink = {},
        onActivate = {},
        onLinkClick = {},
        onLinkLongClick = {},
        timestamp = { modifier -> TimelineEventTimestampView(event = content.first, eventSink = {}, modifier = modifier) },
    )
}
