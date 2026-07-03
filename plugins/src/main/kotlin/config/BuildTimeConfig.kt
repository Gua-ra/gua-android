/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "global.gua"
    const val APPLICATION_NAME = "Gua"
    const val GOOGLE_APP_ID_RELEASE = "1:912726360885:android:d097de99a4c23d2700427c"
    const val GOOGLE_APP_ID_DEBUG = "1:912726360885:android:def0a4e454042e9b00427c"
    const val GOOGLE_APP_ID_NIGHTLY = "1:912726360885:android:e17435e0beb0303000427c"

    // Reverse-DNS of the brand host gua.global. Drives the OIDC custom-scheme redirect
    // (login_redirect_scheme = "global.gua", i.e. global.gua:/oidc) — mirrors iOS.
    val METADATA_HOST_REVERSED: String? = "global.gua"

    // OIDC dynamic client registration requires client_uri, logo_uri, tos_uri and policy_uri
    // to share a single host, which must be the redirect scheme's reverse-DNS — so every URL
    // below lives on gua.global. MAS only validates the hosts; it never fetches these URLs.
    val URL_WEBSITE: String? = "https://gua.global"
    val URL_LOGO: String? = "https://gua.global/icon.png"
    val URL_COPYRIGHT: String? = "https://gua.global/copyright"
    val URL_ACCEPTABLE_USE: String? = "https://gua.global/terms"
    val URL_PRIVACY: String? = "https://gua.global/privacy"
    val URL_POLICY: String? = "https://gua.global/privacy"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null

    const val PUSH_CONFIG_INCLUDE_FIREBASE = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true
}
