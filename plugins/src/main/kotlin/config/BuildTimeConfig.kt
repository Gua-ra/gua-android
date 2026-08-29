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

    // GUA FORK: these were Element's Firebase (vector-alpha, project 912726360885) app IDs and must
    // not ship in the Gua binary. They feed the "google_app_id" string resource in the firebase push
    // provider. Empty = FirebaseApp skips init (no push, no crash). To enable push, set each to the
    // mobilesdk_app_id ("1:<sender>:android:<hash>") of the matching Gua Firebase Android app from
    // its google-services.json, and fill libraries/pushproviders/firebase/.../values/firebase.xml.
    const val GOOGLE_APP_ID_RELEASE = ""
    const val GOOGLE_APP_ID_DEBUG = ""
    const val GOOGLE_APP_ID_NIGHTLY = ""

    // Reverse-DNS of the brand host gua.global. Drives the OIDC custom-scheme redirect
    // (login_redirect_scheme = "global.gua", i.e. global.gua:/oidc) — mirrors iOS.
    val METADATA_HOST_REVERSED: String? = "global.gua"

    // OIDC dynamic client registration requires client_uri, logo_uri, tos_uri and policy_uri
    // to share a single host, which must be the redirect scheme's reverse-DNS — so every URL
    // below lives on gua.global. MAS only validates the hosts; it never fetches these URLs.
    val URL_WEBSITE: String? = "https://gua.global"
    val URL_LOGO: String? = "https://gua.global/gua-icon.png"
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
