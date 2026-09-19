/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.genesis

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory as AndroidPreferenceDataStoreFactory

/**
 * GUA FORK: a [PreferenceDataStoreFactory] that returns THE SAME store for the same name.
 *
 * `FakePreferenceDataStoreFactory` creates a fresh temp file on every call, so two objects built over it
 * never see each other's writes. That would quietly turn the "a stored key survives a restart" test into
 * a test that a brand-new empty store is empty, which is the assertion passing for the wrong reason.
 */
class CachingPreferenceDataStoreFactory : PreferenceDataStoreFactory {
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    override fun create(name: String): DataStore<Preferences> = stores.getOrPut(name) {
        val file = File.createTempFile(name, ".preferences_pb").apply { delete() }
        AndroidPreferenceDataStoreFactory.create { file }
    }
}
