/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.pushproviders.firebase

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * GUA FORK: the account-authority security alert of ADM-009 gate 2, drawn by the app when the system will
 * not draw it.
 *
 * Both push services hand a message to a foregrounded app instead of to the notification tray. Element's
 * own handler then parses it as a Matrix push and discards anything else, so an owner who is looking at
 * their phone, which is exactly who "something is asking for approval over your account" is for, saw
 * nothing at all. Seen on a real device: the alert arrived two seconds after the transition was accepted
 * and was logged as "Invalid data received from Firebase".
 *
 * The server marks these messages, so this is never guessing at a room notification. The words are the
 * server's: they name a device label, a sentence and a time, and nothing else, which is all the channel is
 * allowed to carry.
 */
object GuaAuthorityAlert {

    /** The key identity-service puts beside the alert. Keep in step with AuthorityPushTransport. */
    const val MARKER = "gua_authority_alert"

    private const val CHANNEL_ID = "gua_authority_alert_channel"

    /** Whether this message is one of ours rather than a room notification. */
    fun matches(data: Map<String, String>): Boolean = data[MARKER] == "1"

    /**
     * Shows the alert. A notification id derived from the text, so the same warning arriving twice replaces
     * itself rather than stacking, and two different warnings both stay up.
     */
    fun show(context: Context, data: Map<String, String>) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.gua_authority_alert_channel_name))
                .setDescription(context.getString(R.string.gua_authority_alert_channel_description))
                .build()
        )
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(CHANNEL_ID, (title + body).hashCode(), notification)
        } catch (securityException: SecurityException) {
            // POST_NOTIFICATIONS was not granted. Nothing to do here, and nothing worth crashing a push
            // handler over; the screen still shows the pending transition.
        }
    }
}
