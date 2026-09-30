/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The launch bootstrapper is a backup creator, so it waits for the shared join and refuses on its bound. */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentSessionEncryptionBootstrapperTest {
    @Test
    fun `recovery is not enabled before the shared initialisation has finished`() = runTest {
        var enableCalls = 0
        val initialization = CompletableDeferred<Unit>()
        val encryptionService = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
            awaitE2eeInitializationLambda = {
                initialization.await()
                true
            },
        )
        encryptionService.recoveryStateStateFlow.value = RecoveryState.DISABLED

        SilentSessionEncryptionBootstrapper(A_SESSION_ID, encryptionService, backgroundScope).start()
        // Background work only moves when time does; a whole minute of it changes nothing here.
        advanceTimeBy(1.minutes)
        runCurrent()
        assertThat(enableCalls).isEqualTo(0)

        initialization.complete(Unit)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertThat(enableCalls).isEqualTo(1)
    }

    @Test
    fun `a join that times out refuses to touch key storage`() = runTest {
        var enableCalls = 0
        val encryptionService = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
            awaitE2eeInitializationLambda = { false },
        )
        encryptionService.recoveryStateStateFlow.value = RecoveryState.DISABLED

        SilentSessionEncryptionBootstrapper(A_SESSION_ID, encryptionService, backgroundScope).start()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertThat(enableCalls).isEqualTo(0)
    }

    @Test
    fun `an account whose recovery is already enabled is left alone`() = runTest {
        var enableCalls = 0
        val encryptionService = FakeEncryptionService(
            enableRecoveryLambda = { _, _ ->
                enableCalls++
                Result.success("key")
            },
        )
        encryptionService.recoveryStateStateFlow.value = RecoveryState.ENABLED

        SilentSessionEncryptionBootstrapper(A_SESSION_ID, encryptionService, backgroundScope).start()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertThat(enableCalls).isEqualTo(0)
    }
}
