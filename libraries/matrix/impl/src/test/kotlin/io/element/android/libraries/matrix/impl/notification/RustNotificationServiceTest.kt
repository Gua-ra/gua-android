/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notification

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.IdentityResetInProgressException
import io.element.android.libraries.matrix.api.exception.NotificationResolverException
import io.element.android.libraries.matrix.api.notification.NotificationContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustBatchNotificationResultOk
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustNotificationEventTimeline
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustNotificationItem
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiNotificationClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiTimelineEvent
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.NotificationClient
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.TimelineEventContent

class RustNotificationServiceTest {
    @Test
    fun test() = runTest {
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(AN_EVENT_ID.value to aRustBatchNotificationResultOk()),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.getOrThrow()
        assertThat(result.isEncrypted).isTrue()
        assertThat(result.content).isEqualTo(
            NotificationContent.MessageLike.RoomMessage(
                senderId = A_USER_ID_2,
                messageType = TextMessageType(
                    body = A_MESSAGE,
                    formatted = null,
                )
            )
        )
    }

    @Test
    fun `test mapping invalid item only drops that item`() = runTest {
        val error = IllegalStateException("This event content is not supported")
        val faultyEvent = object : FakeFfiTimelineEvent() {
            override fun content(): TimelineEventContent {
                throw error
            }
        }
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(
                AN_EVENT_ID.value to aRustBatchNotificationResultOk(
                    notificationStatus = NotificationStatus.Event(aRustNotificationItem(aRustNotificationEventTimeline(faultyEvent)))
                ),
                AN_EVENT_ID_2.value to aRustBatchNotificationResultOk()
            ),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val result = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID, AN_EVENT_ID_2))).getOrThrow()
        val exception = result[AN_EVENT_ID]!!.exceptionOrNull()
        assertThat(exception).isEqualTo(error)

        val successfulResult = result[AN_EVENT_ID_2]
        assertThat(successfulResult?.isSuccess).isTrue()
    }

    @Test
    fun `test unable to resolve event`() = runTest {
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = emptyMap(),
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        val exception = sut.getNotifications(mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))).getOrThrow()[AN_EVENT_ID]!!.exceptionOrNull()
        assertThat(exception).isInstanceOf(NotificationResolverException::class.java)
    }

    @Test
    fun `close should invoke the close method of the service`() = runTest {
        val closeResult = lambdaRecorder<Unit> { }
        val notificationClient = FakeFfiNotificationClient(
            closeResult = closeResult,
        )
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
        )
        sut.close()
        closeResult.assertions().isCalledOnce()
    }

    @Test
    fun `a held identity reset stands the fetch down without reaching the SDK`() = runTest {
        val notificationClient = FakeFfiNotificationClient(
            notificationItemResult = mapOf(AN_EVENT_ID.value to aRustBatchNotificationResultOk()),
        )
        val identityResetHold = MutableStateFlow(true)
        val sut = createRustNotificationService(
            notificationClient = notificationClient,
            identityResetHold = identityResetHold,
        )
        val ids = mapOf(A_ROOM_ID to listOf(AN_EVENT_ID))

        val held = sut.getNotifications(ids)
        assertThat(held.exceptionOrNull()).isInstanceOf(IdentityResetInProgressException::class.java)
        assertThat(notificationClient.getNotificationsCallCount).isEqualTo(0)

        identityResetHold.value = false
        val released = sut.getNotifications(ids)
        assertThat(released.isSuccess).isTrue()
        assertThat(notificationClient.getNotificationsCallCount).isEqualTo(1)
    }

    private fun TestScope.createRustNotificationService(
        notificationClient: NotificationClient = FakeFfiNotificationClient(),
        clock: SystemClock = FakeSystemClock(),
        identityResetHold: StateFlow<Boolean> = MutableStateFlow(false),
    ) =
        RustNotificationService(
            sessionId = A_SESSION_ID,
            notificationClient = notificationClient,
            dispatchers = testCoroutineDispatchers(),
            clock = clock,
            identityResetHold = identityResetHold,
        )
}
