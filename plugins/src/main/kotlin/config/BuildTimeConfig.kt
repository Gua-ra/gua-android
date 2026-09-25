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

    // GUA FORK: the Firebase projects and app records the push provider is built against.
    // These replaced Element's (project vector-alpha, 912726360885), which must never ship
    // in a Gua binary.
    //
    // Two projects, because dev and QA must not hold production's credentials. Google scopes
    // a service account to a project and offers nothing narrower, so the only way to keep a
    // dev server from being able to push to the store app is to put the QA and debug packages
    // in a project of their own. identity-service's account-authority channel holds a sending
    // credential for exactly one of these.
    val FIREBASE_PRODUCTION = FirebaseProject(
        projectId = "gua-global",
        senderId = "511804071315",
        apiKey = "AIzaSyB2RTAWSf9v5lqoXILeoc7wzgfImvv1Pxs",
        storageBucket = "gua-global.firebasestorage.app",
    )
    val FIREBASE_DEV = FirebaseProject(
        projectId = "gua-dev",
        senderId = "844160046939",
        apiKey = "AIzaSyDXq3xuvwXIhXkJNZj0Rrv3ntc2YWtkdXw",
        storageBucket = "gua-dev.firebasestorage.app",
    )

    // One app record per package, because Firebase keys them on the package name.
    //
    // RELEASE and DEV are both the release build type: the QA app is the release type built
    // with -Pgua.deployment=dev, which suffixes the applicationId with ".dev". The firebase
    // module picks between them on that same property, so QA registers as itself rather than
    // falling back to production's id and failing.
    val FIREBASE_APP_RELEASE = FirebaseApp("1:511804071315:android:7a87ae8499f379204e1c66", FIREBASE_PRODUCTION)
    val FIREBASE_APP_DEV = FirebaseApp("1:844160046939:android:7a0fd8c65aac441e4a2c7e", FIREBASE_DEV)
    val FIREBASE_APP_DEBUG = FirebaseApp("1:844160046939:android:6387a01892d6abfe4a2c7e", FIREBASE_DEV)

    // Nightly has no Firebase app record: global.gua.nightly is registered in neither project.
    // Left empty so those builds start with push disabled rather than registering as another
    // package.
    val FIREBASE_APP_NIGHTLY = FirebaseApp("", null)

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
