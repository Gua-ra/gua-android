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

    // GUA FORK: the mobilesdk_app_id of each Gua Firebase Android app, feeding the
    // "google_app_id" string resource in the firebase push provider. These replaced
    // Element's (project vector-alpha, 912726360885), which must never ship in a Gua
    // binary. An empty value makes FirebaseApp skip initialisation: no push, no crash.
    //
    // Project "Gua Global" (gua-global, 511804071315). One per package, because Firebase
    // keys its app records on the package name.
    //
    // RELEASE and DEV are both the release build type: the QA app is the release type
    // built with -Pgua.deployment=dev, which suffixes the applicationId with ".dev". The
    // firebase module picks between them on that same property, so QA registers as itself
    // rather than falling back to production's id and failing.
    const val GOOGLE_APP_ID_RELEASE = "1:511804071315:android:7a87ae8499f379204e1c66"
    const val GOOGLE_APP_ID_DEV = "1:511804071315:android:55bc17310919f64c4e1c66"
    const val GOOGLE_APP_ID_DEBUG = "1:511804071315:android:0b8eb92ccf4eaa6a4e1c66"

    // Nightly has no Firebase app record: global.gua.nightly is not registered. Left empty
    // so those builds start with push disabled rather than registering as another package.
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
