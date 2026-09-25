/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.linknewdevice

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiCheckCodeSender
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RustCheckCodeSenderTest {
    @Test
    fun `send invokes the Ffi object`() = runTest {
        val sendResult = lambdaRecorder<UByte, Unit> { }
        val sut = RustCheckCodeSender(
            inner = FakeFfiCheckCodeSender(
                sendResult = sendResult,
            ),
            sessionDispatcher = StandardTestDispatcher(testScheduler),
        )
        sut.send(1.toUByte())
        sendResult.assertions().isCalledOnce().with(value(1.toUByte()))
    }

    /**
     * GUA FORK: there is nothing here that claims to check a code.
     *
     * The test this replaces asserted that `validate` always returned true, which is a test pinning a lie in
     * place. The code is compared inside the secure channel's confirm step, so the only thing this class can
     * be asked for is that it hands the code over, and a failure from the far end arrives on the step flow.
     */
    @Test
    fun `a failure to send is reported rather than swallowed`() = runTest {
        val sut = RustCheckCodeSender(
            inner = FakeFfiCheckCodeSender(sendResult = { throw AN_EXCEPTION }),
            sessionDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertThat(sut.send(1.toUByte()).isFailure).isTrue()
    }

    private companion object {
        private val AN_EXCEPTION = IllegalStateException("the channel is gone")
    }
}
