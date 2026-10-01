/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

/** Not secrets: these values ship in every APK, and Google restricts the API key by package and signing certificate. */
data class FirebaseProject(
    val projectId: String,
    val senderId: String,
    val apiKey: String,
    val storageBucket: String,
)

data class FirebaseApp(
    val googleAppId: String,
    val project: FirebaseProject?,
)
