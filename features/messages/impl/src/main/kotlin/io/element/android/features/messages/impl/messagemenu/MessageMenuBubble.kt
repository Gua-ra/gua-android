/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.canReact
import io.element.android.libraries.matrix.api.core.UniqueId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private const val LIFT_DELAY_MILLIS = 250L
private const val LIFTED_SCALE = 1.05f

fun TimelineItem.Event.canDoubleTapToReact(canSendReaction: Boolean): Boolean {
    return canSendReaction && content is TimelineItemTextBasedContent && content.canReact()
}

fun Modifier.messageMenuBubble(
    id: UniqueId,
    controller: MessageMenuController?,
    interactionSource: InteractionSource,
    liftsWhileHeld: Boolean,
): Modifier = if (controller == null) {
    this
} else {
    this then MessageMenuBubbleElement(id, controller, interactionSource, liftsWhileHeld)
}

private data class MessageMenuBubbleElement(
    val id: UniqueId,
    val controller: MessageMenuController,
    val interactionSource: InteractionSource,
    val liftsWhileHeld: Boolean,
) : ModifierNodeElement<MessageMenuBubbleNode>() {
    override fun create() = MessageMenuBubbleNode(id, controller, interactionSource, liftsWhileHeld)

    override fun update(node: MessageMenuBubbleNode) {
        node.update(id, controller, interactionSource, liftsWhileHeld)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "messageMenuBubble"
    }
}

internal class MessageMenuBubbleNode(
    private var id: UniqueId,
    private var controller: MessageMenuController,
    private var interactionSource: InteractionSource,
    private var liftsWhileHeld: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    var coordinates: LayoutCoordinates? = null
        private set

    private val pressScale = Animatable(1f)
    private var pressJob: Job? = null

    override fun onAttach() {
        controller.register(id, this)
        observePresses()
    }

    override fun onDetach() {
        controller.unregister(id, this)
        coordinates = null
        pressJob = null
    }

    fun update(
        id: UniqueId,
        controller: MessageMenuController,
        interactionSource: InteractionSource,
        liftsWhileHeld: Boolean,
    ) {
        if (id != this.id || controller !== this.controller) {
            this.controller.unregister(this.id, this)
            this.id = id
            this.controller = controller
            controller.register(id, this)
            invalidateDraw()
        }
        if (interactionSource != this.interactionSource || liftsWhileHeld != this.liftsWhileHeld) {
            this.interactionSource = interactionSource
            this.liftsWhileHeld = liftsWhileHeld
            observePresses()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        controller.onBubbleMoved(id)
    }

    override fun ContentDrawScope.draw() {
        val layer = controller.layer
        if (layer != null && controller.liftedId == id) {
            layer.record { this@draw.drawContent() }
            return
        }
        val scale = pressScale.value
        if (scale == 1f) {
            drawContent()
        } else {
            scale(scale) { this@draw.drawContent() }
        }
    }

    private fun observePresses() {
        pressJob?.cancel()
        pressJob = null
        if (!isAttached) return
        if (!liftsWhileHeld) {
            coroutineScope.launch { pressScale.snapTo(1f) }
            return
        }
        pressJob = coroutineScope.launch {
            interactionSource.interactions
                .filterIsInstance<PressInteraction>()
                .collectLatest { interaction ->
                    if (interaction is PressInteraction.Press) {
                        // In the timeline the press is only reported once the tap timeout has passed.
                        delay(LIFT_DELAY_MILLIS - ViewConfiguration.getTapTimeout())
                        pressScale.animateTo(LIFTED_SCALE, spring(stiffness = Spring.StiffnessMediumLow))
                    } else {
                        pressScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                    }
                }
        }
    }
}
