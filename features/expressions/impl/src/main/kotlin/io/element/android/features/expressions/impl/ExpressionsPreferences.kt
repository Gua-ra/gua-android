/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.expressions.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** This device's sticker and GIF data, in the session's directory, which signing out deletes. */
@SingleIn(SessionScope::class)
@Inject
class ExpressionsPreferences(
    matrixClient: MatrixClient,
    @SessionCoroutineScope sessionCoroutineScope: CoroutineScope,
) {
    val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = sessionCoroutineScope) {
        File(matrixClient.sessionPaths.fileDirectory, FILE_NAME)
    }

    private companion object {
        const val FILE_NAME = "expressions.preferences_pb"
    }
}
