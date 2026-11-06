/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.mediastack.grid

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.contentscanner.api.ContentScannerService
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemMediaStackContent
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.contentvalidation.EventContentValidationCache
import io.element.android.libraries.matrix.ui.media.contentvalidation.LocalEventContentValidationState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaStackGridTile(
    val eventId: EventId?,
    val stillSource: MediaSource?,
    // What the content scanner validates before the tile is shown.
    val mediaSources: List<MediaSource>,
    val blurhash: String?,
    val videoDuration: String?,
) : Parcelable

@ContributesNode(RoomScope::class)
@AssistedInject
class MediaStackGridNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val contentScannerService: ContentScannerService,
    private val eventContentValidationCache: EventContentValidationCache,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val kind: TimelineItemMediaStackContent.Kind,
        val tiles: List<MediaStackGridTile>,
    ) : NodeInputs

    interface Callback : Plugin {
        fun onTileClick(index: Int)
    }

    private val inputs = inputs<Inputs>()
    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        CompositionLocalProvider(LocalEventContentValidationState provides eventContentValidationCache) {
            MediaStackGridView(
                kind = inputs.kind,
                tiles = inputs.tiles.toImmutableList(),
                onBackClick = ::navigateUp,
                onTileClick = callback::onTileClick,
                onValidate = contentScannerService::scan,
                modifier = modifier,
            )
        }
    }
}
