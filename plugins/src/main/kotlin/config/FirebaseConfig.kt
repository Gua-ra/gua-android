/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

/**
 * A Firebase project, as the Android SDK reads it out of string resources.
 *
 * GUA FORK: this exists because Gua has two of them. The store app registers in the production
 * project and the QA and debug apps register in a separate dev project, so that the credential a
 * dev server holds cannot address a device running the store build. The split is not cosmetic:
 * Google scopes a service account to a project and offers nothing narrower, so a separate project
 * is the only way to scope the server's sending key at all.
 *
 * A consequence worth stating: an FCM registration token is minted against one project's sender,
 * and a send from another project's credential fails with SENDER_ID_MISMATCH. A QA build pointed
 * at the wrong project therefore receives nothing, silently, which is exactly the failure the
 * account-authority notification channel cannot afford.
 *
 * None of these values is a secret. They ship inside every APK; Google restricts the API key by
 * package name and signing certificate rather than by keeping it hidden.
 */
data class FirebaseProject(
    val projectId: String,
    val senderId: String,
    val apiKey: String,
    val storageBucket: String,
)

/**
 * One Firebase Android app record, which Firebase keys on the package name, inside its project.
 *
 * A null [project] means this variant has no Firebase app at all. The SDK skips initialisation
 * when the app id is empty, so those builds run with push disabled rather than registering as
 * another package.
 */
data class FirebaseApp(
    val googleAppId: String,
    val project: FirebaseProject?,
)
