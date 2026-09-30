/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.notification

import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.IdentityResetInProgressException
import io.element.android.libraries.matrix.api.exception.NotificationResolverException
import io.element.android.libraries.matrix.api.notification.GetNotificationDataResult
import io.element.android.libraries.matrix.api.notification.NotificationService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.BatchNotificationResult
import org.matrix.rustcomponents.sdk.NotificationClient
import org.matrix.rustcomponents.sdk.NotificationItemsRequest
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.use
import timber.log.Timber

class RustNotificationService(
    private val sessionId: SessionId,
    private val notificationClient: NotificationClient,
    private val dispatchers: CoroutineDispatchers,
    clock: SystemClock,
    /** GUA FORK: true while an identity reset holds the sync. Owned by the reset guard. */
    private val identityResetHold: StateFlow<Boolean>,
) : NotificationService {
    private val notificationMapper: NotificationMapper = NotificationMapper(clock)

    override suspend fun getNotifications(
        ids: Map<RoomId, List<EventId>>
    ): GetNotificationDataResult = withContext(dispatchers.io) {
        // GUA FORK: with the main sync stopped, the SDK's notification client takes the free
        // encryption-sync permit and runs its own two-iteration encryption sync, which is the
        // own-user key query the reset guard exists to keep away. Stand down; the push falls back
        // to its generic content and the work retries once the reset is over.
        if (identityResetHold.value) {
            Timber.w("Not fetching notifications: an identity reset holds the sync")
            return@withContext Result.failure(IdentityResetInProgressException())
        }
        runCatchingExceptions {
            val requests = ids.map { (roomId, eventIds) ->
                NotificationItemsRequest(
                    roomId = roomId.value,
                    eventIds = eventIds.map { it.value }
                )
            }
            val items = notificationClient.getNotifications(requests)
            buildMap {
                val eventIds = requests.flatMap { it.eventIds }.distinct()
                for (rawEventId in eventIds) {
                    val roomId = RoomId(requests.find { it.eventIds.contains(rawEventId) }?.roomId!!)
                    val eventId = EventId(rawEventId)
                    items[rawEventId].use { result ->
                        when (result) {
                            is BatchNotificationResult.Ok -> {
                                when (val status = result.status) {
                                    is NotificationStatus.Event -> {
                                        val result = notificationMapper.map(sessionId, eventId, roomId, status.item)
                                        result.onFailure { Timber.e(it, "Could not map notification event $eventId") }
                                        put(eventId, result)
                                    }
                                    is NotificationStatus.EventNotFound -> {
                                        Timber.e("Could not retrieve event for notification with $eventId - event not found")
                                        put(eventId, Result.failure(NotificationResolverException.EventNotFound))
                                    }
                                    is NotificationStatus.EventFilteredOut -> {
                                        Timber.d("Could not retrieve event for notification with $eventId - event filtered out")
                                        put(eventId, Result.failure(NotificationResolverException.EventFilteredOut))
                                    }
                                    NotificationStatus.EventRedacted -> {
                                        Timber.d("Could not retrieve event for notification with $eventId - event redacted")
                                        put(eventId, Result.failure(NotificationResolverException.EventRedacted))
                                    }
                                }
                            }
                            is BatchNotificationResult.Error -> {
                                Timber.e("Error while retrieving notification with $rawEventId - ${result.message}")
                                put(
                                    eventId,
                                    Result.failure(NotificationResolverException.UnknownError(result.message))
                                )
                            }
                            null -> {
                                Timber.e("The notification data for $rawEventId was not in the retrieved results. This is unexpected.")
                                put(
                                    eventId,
                                    Result.failure(NotificationResolverException.UnknownError("Notification data not found"))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun close() {
        notificationClient.close()
    }
}
