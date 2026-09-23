/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.linknewdevice

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.linknewdevice.CheckCodeSender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.CheckCodeSender as FfiCheckCodeSender

/**
 * GUA FORK: a deliberate divergence from upstream element-x-android, which carries a `validate` that returns
 * true on both branches without consulting the SDK.
 *
 * The verdict on a check code comes from the ceremony: `send` hands it to the secure channel, and a wrong code
 * fails the flow with `InvalidCheckCode` rather than being reported here. Nothing in this class can answer
 * "was that code right" before then, so nothing in this class claims to.
 */
class RustCheckCodeSender(
    private val inner: FfiCheckCodeSender,
    private val sessionDispatcher: CoroutineDispatcher,
) : CheckCodeSender {
    override suspend fun send(code: UByte): Result<Unit> = withContext(sessionDispatcher) {
        runCatchingExceptions {
            inner.send(code)
        }
    }
}
