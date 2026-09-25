/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object PushConfig {
    /**
     * Note: pusher_app_id cannot exceed 64 chars.
     *
     * GUA FORK: this is the key Sygnal looks the notification up under, so it has to
     * match the app_id in the gateway's config exactly; a mismatch is answered with a
     * 404 the user never sees. One value covers the production, QA and debug packages:
     * on Android the registration token decides both which Firebase project a send must
     * come from and which app it reaches, so nothing is routed on this string. It is
     * also what the account-authority notification channel registers under, where the
     * server uses it for the APNs topic and ignores it for FCM.
     */
    const val PUSHER_APP_ID: String = "global.gua.android"
}
