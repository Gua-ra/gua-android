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
import io.element.android.libraries.matrix.api.core.UniqueId
import kotlinx.collections.immutable.ImmutableList

/**
 * Photos and videos sent together, shown as one stack. [members] are the image and video events in the order they were picked; the
 * stack event itself is the first of them, so reactions, replies and threads act on it.
 */
@Immutable
data class TimelineItemMediaStackContent(
    val stackId: UniqueId,
    val members: ImmutableList<TimelineItem.Event>,
) : TimelineItemEventContent {
    override val type: String = "TimelineItemMediaStackContent"

    val first: TimelineItem.Event get() = members.first()

    val caption: String? get() = (first.content as? TimelineItemEventContentWithAttachment)?.caption

    val formattedCaption: CharSequence? get() = (first.content as? TimelineItemEventContentWithAttachment)?.formattedCaption

    val kind: Kind = when {
        members.all { it.content is TimelineItemImageContent } -> Kind.Photos
        members.all { it.content is TimelineItemVideoContent } -> Kind.Videos
        else -> Kind.Items
    }

    val failedMembers: List<TimelineItem.Event> get() = members.filter { it.failedToSend }

    enum class Kind(
        @PluralsRes val headerPlural: Int,
        @PluralsRes val removalTitlePlural: Int,
    ) {
        Photos(R.plurals.gua_media_stack_photos, R.plurals.gua_media_stack_remove_photos_title),
        Videos(R.plurals.gua_media_stack_videos, R.plurals.gua_media_stack_remove_videos_title),
        Items(R.plurals.gua_media_stack_items, R.plurals.gua_media_stack_remove_items_title),
    }
}
