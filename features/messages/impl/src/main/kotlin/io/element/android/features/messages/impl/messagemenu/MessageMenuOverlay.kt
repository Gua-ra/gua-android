/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.timeline.a11y.a11yReactionAction
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.text.toSp
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val BUBBLE_ID = "bubble"
private const val REACTION_BAR_ID = "reactionBar"
private const val DETAILS_ID = "details"

private val SPACING = 8.dp
private val MARGIN = 12.dp
private val MINIMUM_BUBBLE_HEIGHT = 44.dp
private val REACTION_SIZE = 44.dp
private val REACTION_SPACING = 2.dp
private val REACTION_BAR_PADDING = 6.dp
private val DETAILS_WIDTH = 250.dp
private val REACTION_ROW_HEIGHT = 48.dp
private const val VISIBLE_REACTION_ROWS = 5.5f
private const val CLOSE_DURATION_MILLIS = 180

@Composable
fun MessageMenuOverlay(
    state: MessageMenuState,
    controller: MessageMenuController,
    onReactionClick: (emoji: String, TimelineItem.Event) -> Unit,
    onAddReactionClick: (TimelineItem.Event) -> Unit,
    onActionClick: (TimelineItemAction, TimelineItem.Event) -> Unit,
    onSelectClick: (TimelineItem.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = state.target as? MessageMenuState.Target.Shown ?: return
    key(target.event.id, target.gesture) {
        MessageMenuOverlayContent(
            target = target,
            controller = controller,
            onDismiss = { state.eventSink(MessageMenuEvent.Dismiss) },
            onReactionClick = onReactionClick,
            onAddReactionClick = onAddReactionClick,
            onActionClick = onActionClick,
            onSelectClick = onSelectClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun MessageMenuOverlayContent(
    target: MessageMenuState.Target.Shown,
    controller: MessageMenuController,
    onDismiss: () -> Unit,
    onReactionClick: (emoji: String, TimelineItem.Event) -> Unit,
    onAddReactionClick: (TimelineItem.Event) -> Unit,
    onActionClick: (TimelineItemAction, TimelineItem.Event) -> Unit,
    onSelectClick: (TimelineItem.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val event = target.event
    val layer = rememberGraphicsLayer()
    DisposableEffect(event.id, layer) {
        controller.lift(event.id, layer)
        onDispose { controller.drop() }
    }
    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        controller.progress.snapTo(0f)
        withFrameNanos {}
        // The bubble can leave the timeline between the gesture and the first frame of the overlay.
        if (!controller.hasBubble(event.id)) {
            latestOnDismiss()
            return@LaunchedEffect
        }
        controller.progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    fun close(then: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        coroutineScope.launch {
            controller.progress.animateTo(0f, tween(CLOSE_DURATION_MILLIS))
            latestOnDismiss()
            then()
        }
    }

    BackHandler { close() }

    val configuration = LocalConfiguration.current
    val initialConfiguration = remember { Configuration(configuration) }
    LaunchedEffect(configuration) {
        if (configuration.diff(initialConfiguration) != 0) {
            latestOnDismiss()
        }
    }

    val haptics = LocalHapticFeedback.current
    val showsDetails = when (target.gesture) {
        MessageMenuGesture.LongPress -> !target.sections.isEmpty
        MessageMenuGesture.DoubleTap -> target.reactionRows.isNotEmpty()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Backdrop(
            controller = controller,
            onClick = { close() },
        )
        MessageMenuLayoutContainer(
            target = target,
            controller = controller,
            bubble = { LiftedBubble(layer = layer) },
            reactionBar = if (target.reactions.isNotEmpty()) {
                {
                    ReactionBar(
                        reactions = target.reactions,
                        highlightedReactions = target.highlightedReactions,
                        onReactionClick = { emoji ->
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onReactionClick(emoji, event)
                            close()
                        },
                        onAddReactionClick = { close { onAddReactionClick(event) } },
                    )
                }
            } else {
                null
            },
            details = if (showsDetails) {
                {
                    DetailsCard {
                        when (target.gesture) {
                            MessageMenuGesture.LongPress -> ActionList(
                                sections = target.sections,
                                onActionClick = { action ->
                                    when (action) {
                                        MessageMenuAction.Select -> close { onSelectClick(event) }
                                        is MessageMenuAction.Timeline -> close { onActionClick(action.action, event) }
                                    }
                                },
                            )
                            MessageMenuGesture.DoubleTap -> ReactionRows(rows = target.reactionRows)
                        }
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun MessageMenuLayoutContainer(
    target: MessageMenuState.Target.Shown,
    controller: MessageMenuController,
    bubble: @Composable () -> Unit,
    reactionBar: (@Composable () -> Unit)?,
    details: (@Composable () -> Unit)?,
) {
    val eventId = target.event.id
    val layoutDirection = LocalLayoutDirection.current
    val alignsRight = target.event.isMine != (layoutDirection == LayoutDirection.Rtl)
    val reactionCount = target.reactions.size
    val limitsDetailsToReactionRows = target.gesture == MessageMenuGesture.DoubleTap
    val insets = WindowInsets.safeDrawing
    val lastSource = remember { SourceHolder() }

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(Modifier.layoutId(BUBBLE_ID)) { bubble() }
            reactionBar?.let { Box(Modifier.layoutId(REACTION_BAR_ID)) { it() } }
            details?.let { Box(Modifier.layoutId(DETAILS_ID)) { it() } }
        },
    ) { measurables, constraints ->
        // Read so the layout runs again whenever the real bubble moves.
        controller.positionVersion
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bubbleCoordinates = controller.coordinatesOf(eventId)
        val sourceSize = bubbleCoordinates?.size?.toSize() ?: lastSource.rect?.size
        if (sourceSize == null) {
            return@Layout layout(width, height) {}
        }
        val spacing = SPACING.toPx()
        val margin = MARGIN.toPx()
        val container = Rect(
            left = insets.getLeft(this, layoutDirection).toFloat(),
            top = insets.getTop(this) + spacing,
            right = (width - insets.getRight(this, layoutDirection)).toFloat(),
            bottom = height - insets.getBottom(this) - spacing,
        )
        val barSize = reactionBar?.let {
            val reactionsWidth = reactionCount * REACTION_SIZE.toPx() + (reactionCount - 1) * REACTION_SPACING.toPx() + 2 * REACTION_BAR_PADDING.toPx()
            Size(
                width = min(reactionsWidth + REACTION_SIZE.toPx() + REACTION_BAR_PADDING.toPx(), container.width - 2 * margin).coerceAtLeast(0f),
                height = REACTION_SIZE.toPx() + 2 * REACTION_BAR_PADDING.toPx(),
            )
        }
        val barPlaceable = barSize?.let { size ->
            measurables.first { it.layoutId == REACTION_BAR_ID }.measure(Constraints.fixed(size.width.roundToInt(), size.height.roundToInt()))
        }
        val barSpace = barSize?.let { it.height + spacing } ?: 0f
        val detailsWidth = min(DETAILS_WIDTH.toPx(), container.width - 2 * margin).coerceAtLeast(0f).roundToInt()
        val maxDetailsHeight = max(0f, container.height - barSpace - min(sourceSize.height, MINIMUM_BUBBLE_HEIGHT.toPx()) - spacing).let {
            if (limitsDetailsToReactionRows) min(it, VISIBLE_REACTION_ROWS * REACTION_ROW_HEIGHT.toPx()) else it
        }
        val detailsPlaceable = details?.let {
            measurables.first { measurable -> measurable.layoutId == DETAILS_ID }.measure(
                Constraints(minWidth = detailsWidth, maxWidth = detailsWidth, minHeight = 0, maxHeight = maxDetailsHeight.roundToInt())
            )
        }

        fun menuLayoutFor(source: Rect) = MessageMenuLayout.compute(
            source = source,
            alignsRight = alignsRight,
            container = container,
            reactionBarSize = barSize,
            detailsSize = detailsPlaceable?.let { Size(it.width.toFloat(), it.height.toFloat()) },
            spacing = spacing,
            margin = margin,
            minimumBubbleHeight = MINIMUM_BUBBLE_HEIGHT.toPx(),
        )

        val bubbleSize = menuLayoutFor(Rect(Offset.Zero, sourceSize)).bubble.size
        val bubblePlaceable = measurables.first { it.layoutId == BUBBLE_ID }.measure(
            Constraints.fixed(bubbleSize.width.roundToInt().coerceAtLeast(0), bubbleSize.height.roundToInt().coerceAtLeast(0))
        )
        layout(width, height) {
            // Resolved while placing, so the lifted bubble is already in place on the overlay's first frame.
            val overlay = coordinates
            val source = if (overlay != null && bubbleCoordinates != null && bubbleCoordinates.isAttached) {
                overlay.localBoundingBoxOf(bubbleCoordinates, clipBounds = false).also { lastSource.rect = it }
            } else {
                lastSource.rect
            } ?: return@layout
            val menuLayout = menuLayoutFor(source)
            val progress = controller.progress.value
            bubblePlaceable.place(
                x = lerp(source.left, menuLayout.bubble.left, progress).roundToInt(),
                y = lerp(source.top, menuLayout.bubble.top, progress).roundToInt(),
            )
            val appearScale = lerp(0.5f, 1f, progress)
            menuLayout.reactionBar?.let { frame ->
                barPlaceable?.placeWithLayer(frame.left.roundToInt(), frame.top.roundToInt()) {
                    alpha = progress
                    scaleX = appearScale
                    scaleY = appearScale
                    transformOrigin = TransformOrigin(if (alignsRight) 1f else 0f, 1f)
                }
            }
            menuLayout.details?.let { frame ->
                detailsPlaceable?.placeWithLayer(frame.left.roundToInt(), frame.top.roundToInt()) {
                    alpha = progress
                    scaleX = appearScale
                    scaleY = appearScale
                    transformOrigin = TransformOrigin(if (alignsRight) 1f else 0f, 0f)
                }
            }
        }
    }
}

private class SourceHolder {
    var rect: Rect? = null
}

@Composable
private fun Backdrop(
    controller: MessageMenuController,
    onClick: () -> Unit,
) {
    // Below API 31 there is no blur behind the overlay, so the dim does all the work.
    val scrim = Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.15f else 0.4f)
    val latestOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = controller.progress.value }
            .background(scrim)
            .pointerInput(Unit) { detectTapGestures { latestOnClick() } }
            .clearAndSetSemantics {}
    )
}

@Composable
private fun LiftedBubble(layer: GraphicsLayer) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .drawBehind { drawLayer(layer) }
    )
}

@Composable
private fun ReactionBar(
    reactions: ImmutableList<String>,
    highlightedReactions: ImmutableList<String>,
    onReactionClick: (String) -> Unit,
    onAddReactionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        color = ElementTheme.colors.bgCanvasDefaultLevel1,
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = REACTION_BAR_PADDING),
                horizontalArrangement = Arrangement.spacedBy(REACTION_SPACING),
            ) {
                reactions.forEach { emoji ->
                    ReactionButton(
                        emoji = emoji,
                        isHighlighted = emoji in highlightedReactions,
                        onClick = { onReactionClick(emoji) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(end = REACTION_BAR_PADDING)
                    .size(REACTION_SIZE)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAddReactionClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.ReactionAdd(),
                    contentDescription = stringResource(CommonStrings.a11y_react_with_other_emojis),
                    tint = ElementTheme.colors.iconSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReactionButton(
    emoji: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
) {
    val highlight = if (isHighlighted) {
        Modifier
            .background(ElementTheme.colors.bgSubtlePrimary, CircleShape)
            .border(1.dp, ElementTheme.colors.borderInteractivePrimary, CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(REACTION_SIZE)
            .clip(CircleShape)
            .then(highlight)
            .clickable(
                role = Role.Button,
                onClickLabel = a11yReactionAction(emoji = emoji, userAlreadyReacted = isHighlighted),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = ElementTheme.typography.fontBodyLgRegular.copy(
                fontSize = 28.dp.toSp(),
                lineHeight = 34.dp.toSp(),
            ),
        )
    }
}

@Composable
private fun DetailsCard(
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ElementTheme.colors.bgCanvasDefaultLevel1,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            content()
        }
    }
}

@Composable
private fun ActionList(
    sections: MessageMenuSections,
    onActionClick: (MessageMenuAction) -> Unit,
) {
    var isShowingMore by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isShowingMore) {
            MenuRow(
                title = stringResource(R.string.gua_action_more),
                icon = { MenuIcon(imageVector = CompoundIcons.ChevronLeft(), tint = ElementTheme.colors.iconPrimary) },
                isEmphasized = true,
                onClick = { isShowingMore = false },
            )
            HorizontalDivider()
            sections.more.forEach { action -> ActionRow(action = action, onClick = { onActionClick(action) }) }
        } else {
            sections.primary.forEach { action -> ActionRow(action = action, onClick = { onActionClick(action) }) }
            if (sections.more.isNotEmpty()) {
                MenuRow(
                    title = stringResource(R.string.gua_action_more),
                    icon = { MenuIcon(imageVector = CompoundIcons.OverflowHorizontal(), tint = ElementTheme.colors.iconPrimary) },
                    trailing = { MenuIcon(imageVector = CompoundIcons.ChevronRight(), tint = ElementTheme.colors.iconTertiary) },
                    onClick = { isShowingMore = true },
                )
            }
            if (sections.destructive.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(ElementTheme.colors.bgSubtlePrimary)
                )
                sections.destructive.forEach { action -> ActionRow(action = action, onClick = { onActionClick(action) }) }
            }
        }
    }
}

@Composable
private fun ActionRow(
    action: MessageMenuAction,
    onClick: () -> Unit,
) {
    when (action) {
        MessageMenuAction.Select -> MenuRow(
            title = stringResource(R.string.gua_action_select),
            icon = { MenuIcon(imageVector = Icons.Outlined.SelectAll, tint = ElementTheme.colors.iconPrimary) },
            onClick = onClick,
        )
        is MessageMenuAction.Timeline -> {
            val tint = if (action.action.destructive) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconPrimary
            val titleRes = if (action.action == TimelineItemAction.Redact) CommonStrings.action_remove_message else action.action.titleRes
            MenuRow(
                title = stringResource(titleRes),
                icon = { Icon(resourceId = action.action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) },
                isDestructive = action.action.destructive,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    isEmphasized: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = if (isEmphasized) ElementTheme.typography.fontBodyLgMedium else ElementTheme.typography.fontBodyLgRegular,
            color = if (isDestructive) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
        )
        trailing?.invoke()
    }
}

@Composable
private fun MenuIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun ReactionRows(
    rows: ImmutableList<MessageReactionRow>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = REACTION_ROW_HEIGHT)
                    .padding(horizontal = 16.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(
                    avatarData = row.avatarData,
                    avatarType = AvatarType.User,
                )
                Text(
                    text = if (row.isCurrentUser) stringResource(CommonStrings.common_you) else row.name,
                    modifier = Modifier.weight(1f),
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.keys.joinToString(" "),
                    modifier = Modifier.widthIn(max = 110.dp),
                    style = ElementTheme.typography.fontBodyLgRegular.copy(
                        fontSize = 20.dp.toSp(),
                        lineHeight = 26.dp.toSp(),
                    ),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
