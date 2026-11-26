/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class DefaultGifAutoplayStoreTest : RobolectricTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `GIFs play automatically by default`() {
        assertThat(DefaultGifAutoplayStore(context).playGifsAutomatically.value).isTrue()
    }

    @Test
    fun `turning it off is kept on the device`() {
        val store = DefaultGifAutoplayStore(context)

        store.setPlayGifsAutomatically(false)

        assertThat(store.playGifsAutomatically.value).isFalse()
        assertThat(DefaultGifAutoplayStore(context).playGifsAutomatically.value).isFalse()
    }

    @Test
    fun `turning it back on is kept on the device`() {
        DefaultGifAutoplayStore(context).setPlayGifsAutomatically(false)
        val store = DefaultGifAutoplayStore(context)

        store.setPlayGifsAutomatically(true)

        assertThat(store.playGifsAutomatically.value).isTrue()
        assertThat(DefaultGifAutoplayStore(context).playGifsAutomatically.value).isTrue()
    }
}
