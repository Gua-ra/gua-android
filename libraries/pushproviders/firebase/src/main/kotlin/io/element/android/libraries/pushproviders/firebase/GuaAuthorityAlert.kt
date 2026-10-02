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

object GuaAuthorityAlert {
    const val MARKER = "gua_authority_alert"

    private const val CHANNEL_ID = "gua_authority_alert_channel"

    fun matches(data: Map<String, String>): Boolean = data[MARKER] == "1"

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
            // POST_NOTIFICATIONS was not granted.
        }
    }
}
