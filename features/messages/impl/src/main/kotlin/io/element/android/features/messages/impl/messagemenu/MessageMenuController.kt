/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagemenu

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.core.UniqueId

@Stable
class MessageMenuController {
    var liftedId: UniqueId? by mutableStateOf(null)
        private set

    var layer: GraphicsLayer? by mutableStateOf(null)
        private set

    val progress = Animatable(0f)

    var positionVersion: Int by mutableIntStateOf(0)
        private set

    var onDoubleTap: (TimelineItem.Event) -> Unit = {}

    var onKeyboardMenu: (TimelineItem.Event) -> Unit = {}

    private val bubbles = HashMap<UniqueId, MessageMenuBubbleNode>()

    fun lift(id: UniqueId, layer: GraphicsLayer) {
        this.layer = layer
        liftedId = id
    }

    fun drop() {
        liftedId = null
        layer = null
    }

    fun coordinatesOf(id: UniqueId): LayoutCoordinates? = bubbles[id]?.coordinates?.takeIf { it.isAttached }

    fun hasBubble(id: UniqueId): Boolean = coordinatesOf(id) != null

    // combinedClickable also long clicks and double clicks from Enter, and the overlay cannot take keyboard focus.
    fun isKeyboardGesture(id: UniqueId): Boolean = bubbles[id]?.lastInputWasKey == true

    internal fun pressScaleOf(id: UniqueId): Float? = bubbles[id]?.scale

    internal fun register(id: UniqueId, node: MessageMenuBubbleNode) {
        bubbles[id] = node
    }

    internal fun unregister(id: UniqueId, node: MessageMenuBubbleNode) {
        if (bubbles[id] === node) {
            bubbles.remove(id)
        }
    }

    internal fun onBubbleMoved(id: UniqueId) {
        if (id == liftedId) {
            positionVersion++
        }
    }
}

@Composable
fun rememberMessageMenuController(): MessageMenuController = remember { MessageMenuController() }

val LocalMessageMenuController = staticCompositionLocalOf<MessageMenuController?> { null }
