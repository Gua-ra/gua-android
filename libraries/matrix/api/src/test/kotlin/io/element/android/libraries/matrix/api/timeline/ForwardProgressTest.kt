/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.timeline

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ForwardProgressTest {
    @Test
    fun `forwarding the events one call at a time fails instead of skipping all but the first`() = runTest {
        val progress = ForwardProgress(listOf(AN_EVENT_ID, AN_EVENT_ID_2))
        val sends = mutableListOf<EventId>()

        val errors = listOf(AN_EVENT_ID, AN_EVENT_ID_2).map { eventId ->
            runCatchingExceptions {
                progress.forwardTo(A_ROOM_ID, listOf(eventId)) { _, _ -> sends += eventId }
            }.exceptionOrNull()
        }

        errors.forEach { assertThat(it).isInstanceOf(IllegalArgumentException::class.java) }
        assertThat(sends).isEmpty()
        assertThat(progress.hasSentAll(A_ROOM_ID)).isFalse()
    }

    @Test
    fun `a room has everything only once every event of the forward is sent to it`() = runTest {
        val eventIds = listOf(AN_EVENT_ID, AN_EVENT_ID_2)
        val progress = ForwardProgress(eventIds)

        val failed = runCatchingExceptions {
            progress.forwardTo(A_ROOM_ID, eventIds) { index, _ -> check(index == 0) { "Sending failed" } }
        }
        assertThat(failed.isFailure).isTrue()
        assertThat(progress.hasSentAll(A_ROOM_ID)).isFalse()

        progress.forwardTo(A_ROOM_ID, eventIds) { _, _ -> }
        assertThat(progress.hasSentAll(A_ROOM_ID)).isTrue()
    }
}
