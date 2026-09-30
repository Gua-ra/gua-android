/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.encryption

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.encryption.IdentityResetGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow

class FakeIdentityResetGuard(
    private val operationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    var acquireResult: Boolean = true,
    var acquireLambda: suspend () -> Unit = {},
    var releaseIfIdleLambda: suspend () -> Unit = {},
    var runResetLambda: ((suspend () -> Result<Unit>) -> Deferred<Result<Unit>>)? = null,
) : IdentityResetGuard {
    override val isHeld: MutableStateFlow<Boolean> = MutableStateFlow(false)

    var acquireCallCount: Int = 0
        private set
    var releaseIfIdleCallCount: Int = 0
        private set
    var runResetCallCount: Int = 0
        private set

    override suspend fun acquire(): Boolean {
        acquireCallCount++
        acquireLambda()
        if (acquireResult) isHeld.value = true
        return acquireResult
    }

    override fun runReset(operation: suspend () -> Result<Unit>): Deferred<Result<Unit>> {
        runResetCallCount++
        runResetLambda?.let { return it(operation) }
        return operationScope.async {
            try {
                runCatchingExceptions { operation().getOrThrow() }
            } finally {
                isHeld.value = false
            }
        }
    }

    override suspend fun releaseIfIdle() {
        releaseIfIdleCallCount++
        isHeld.value = false
        releaseIfIdleLambda()
    }
}
