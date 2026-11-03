/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.canReact
import io.element.android.libraries.matrix.api.core.UniqueId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIFT_DELAY_MILLIS = 250L
private const val LIFTED_SCALE = 1.05f

fun TimelineItem.Event.canDoubleTapToReact(canSendReaction: Boolean): Boolean {
    return canSendReaction && !failedToSend && content is TimelineItemTextBasedContent && content.canReact()
}

// onKeyboardMenu takes Shift+F10 from the bubble and its content, because the overlay cannot take keyboard focus.
fun Modifier.messageMenuBubble(
    id: UniqueId,
    controller: MessageMenuController?,
    liftsWhileHeld: Boolean,
    onKeyboardMenu: (() -> Unit)? = null,
): Modifier = if (controller == null) {
    this
} else {
    this then MessageMenuBubbleElement(id, controller, liftsWhileHeld, onKeyboardMenu)
}

private data class MessageMenuBubbleElement(
    val id: UniqueId,
    val controller: MessageMenuController,
    val liftsWhileHeld: Boolean,
    val onKeyboardMenu: (() -> Unit)?,
) : ModifierNodeElement<MessageMenuBubbleNode>() {
    override fun create() = MessageMenuBubbleNode(id, controller, liftsWhileHeld, onKeyboardMenu)

    override fun update(node: MessageMenuBubbleNode) {
        node.update(id, controller, liftsWhileHeld, onKeyboardMenu)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "messageMenuBubble"
    }
}

internal class MessageMenuBubbleNode(
    private var id: UniqueId,
    private var controller: MessageMenuController,
    private var liftsWhileHeld: Boolean,
    private var onKeyboardMenu: (() -> Unit)?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, PointerInputModifierNode, KeyInputModifierNode {
    var coordinates: LayoutCoordinates? = null
        private set

    var lastInputWasKey = false
        private set

    private var pressScale = Animatable(1f)
    val scale: Float get() = pressScale.value
    private var pressJob: Job? = null

    override fun onAttach() {
        controller.register(id, this)
    }

    override fun onDetach() {
        controller.unregister(id, this)
        coordinates = null
        lastInputWasKey = false
        pressJob = null
        // A reused row would otherwise keep the scale of a press it was detached in the middle of.
        pressScale = Animatable(1f)
    }

    fun update(
        id: UniqueId,
        controller: MessageMenuController,
        liftsWhileHeld: Boolean,
        onKeyboardMenu: (() -> Unit)?,
    ) {
        this.onKeyboardMenu = onKeyboardMenu
        if (id != this.id || controller !== this.controller) {
            this.controller.unregister(this.id, this)
            this.id = id
            this.controller = controller
            lastInputWasKey = false
            controller.register(id, this)
            invalidateDraw()
        }
        if (liftsWhileHeld != this.liftsWhileHeld) {
            this.liftsWhileHeld = liftsWhileHeld
            release()
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

    // Watched here rather than through an interaction source, because media with a caption and galleries handle their own presses.
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        val changes = pointerEvent.changes
        if (pass == PointerEventPass.Initial && changes.any { it.changedToDownIgnoreConsumed() }) lastInputWasKey = false
        if (!liftsWhileHeld) return
        when (pass) {
            PointerEventPass.Initial -> {
                if (changes.count { it.pressed } != 1) {
                    release()
                } else if (changes.any { it.changedToDownIgnoreConsumed() }) {
                    press()
                }
            }
            PointerEventPass.Final -> {
                val isTakenOver = changes.any { change ->
                    change.pressed &&
                        change.positionChangeIgnoreConsumed() != Offset.Zero &&
                        (change.isConsumed || change.isOutOfBounds(bounds, Size.Zero))
                }
                if (isTakenOver) release()
            }
            PointerEventPass.Main -> Unit
        }
    }

    override fun onCancelPointerInput() {
        release()
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) lastInputWasKey = true
        val onKeyboardMenu = onKeyboardMenu ?: return false
        if (event.key != Key.F10 || !event.isShiftPressed) return false
        if (event.type == KeyEventType.KeyUp) onKeyboardMenu()
        return true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    private fun press() {
        pressJob?.cancel()
        pressJob = coroutineScope.launch {
            delay(LIFT_DELAY_MILLIS)
            pressScale.animateTo(LIFTED_SCALE, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    private fun release() {
        val job = pressJob ?: return
        job.cancel()
        pressJob = null
        if (isAttached) {
            coroutineScope.launch { pressScale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }
}
