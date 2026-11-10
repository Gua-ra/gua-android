/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.media.MediaSource
import kotlinx.collections.immutable.ImmutableList

// The stack event is a copy of its first member, so reactions, replies and threads act on that member.
@Immutable
data class TimelineItemMediaStackContent(
    val stackId: UniqueId,
    val members: ImmutableList<MediaStackMember>,
    // The member that arrived last in the timeline; members are in the order they were picked.
    val newest: TimelineItem.Event,
    // The read receipt goes to the newest member in timeline order that is sent, so a member not sent yet never holds it back.
    val newestEventId: EventId?,
) : TimelineItemEventContent {
    override val type: String = "TimelineItemMediaStackContent"

    val first: TimelineItem.Event get() = members.first().event

    val caption: String? get() = members.first().media.caption

    val formattedCaption: CharSequence? get() = members.first().media.formattedCaption

    val kind: Kind = when {
        members.all { it is MediaStackMember.Photo } -> Kind.Photos
        members.all { it is MediaStackMember.Video } -> Kind.Videos
        else -> Kind.Items
    }

    val failedMembers: List<TimelineItem.Event> get() = members.map { it.event }.filter { it.failedToSend }

    enum class Kind(
        @PluralsRes val headerPlural: Int,
        @PluralsRes val removalTitlePlural: Int,
    ) {
        Photos(R.plurals.gua_media_stack_photos, R.plurals.gua_media_stack_remove_photos_title),
        Videos(R.plurals.gua_media_stack_videos, R.plurals.gua_media_stack_remove_videos_title),
        Items(R.plurals.gua_media_stack_items, R.plurals.gua_media_stack_remove_items_title),
    }
}

@Immutable
sealed interface MediaStackMember {
    val event: TimelineItem.Event
    val media: TimelineItemEventContentWithAttachment
    val thumbnailSource: MediaSource?

    // A card or a grid tile shows a still: the thumbnail of a GIF is not animated, and a video never plays in a stack.
    val stillSource: MediaSource?

    data class Photo(
        override val event: TimelineItem.Event,
        override val media: TimelineItemImageContent,
    ) : MediaStackMember {
        override val thumbnailSource get() = media.thumbnailSource
        override val stillSource get() = media.thumbnailSource ?: media.mediaSource
    }

    data class Video(
        override val event: TimelineItem.Event,
        override val media: TimelineItemVideoContent,
    ) : MediaStackMember {
        override val thumbnailSource get() = media.thumbnailSource
        override val stillSource get() = media.thumbnailSource
    }
}

fun TimelineItem.Event.toMediaStackMember(): MediaStackMember? = when (content) {
    is TimelineItemImageContent -> MediaStackMember.Photo(this, content)
    is TimelineItemVideoContent -> MediaStackMember.Video(this, content)
    else -> null
}

// A stack shows the send failure of one of its members, which is the one to resolve or retry.
fun TimelineItem.Event.sendFailureSource(): TimelineItem.Event = (content as? TimelineItemMediaStackContent)?.failedMembers?.firstOrNull() ?: this
