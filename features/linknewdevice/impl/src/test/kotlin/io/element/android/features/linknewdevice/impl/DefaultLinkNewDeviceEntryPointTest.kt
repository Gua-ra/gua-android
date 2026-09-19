/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.testing.junit4.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeSessionEnterpriseService
import io.element.android.features.linknewdevice.api.LinkNewDeviceEntryPoint
import io.element.android.features.linknewdevice.impl.screens.grantauthority.FakeAccountAuthorityManager
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.authority.DefaultLinkedDeviceAuthorityKeySource
import io.element.android.libraries.guaresolver.authority.DeviceGrantCandidate
import io.element.android.libraries.guaresolver.authority.LinkedDeviceAuthorityKeySource
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DefaultLinkNewDeviceEntryPointTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `test node creation`() = runTest {
        val entryPoint = DefaultLinkNewDeviceEntryPoint()
        val client = FakeMatrixClient()
        val parentNode = TestParentNode.create { buildContext, plugins ->
            LinkNewDeviceFlowNode(
                buildContext = buildContext,
                plugins = plugins,
                sessionCoroutineScope = backgroundScope,
                linkNewMobileHandler = LinkNewMobileHandler(client),
                linkNewDesktopHandler = LinkNewDesktopHandler(client),
                sessionEnterpriseService = FakeSessionEnterpriseService(),
                sessionId = A_SESSION_ID,
                // GUA FORK: ADM-009. Off by default, so the flow ends where it always ended.
                featureFlagService = FakeFeatureFlagService(),
                sessionStore = InMemorySessionStore(),
                authorityManager = FakeAccountAuthorityManager(),
                linkedDeviceAuthorityKeySource = DefaultLinkedDeviceAuthorityKeySource(),
            )
        }
        val callback: LinkNewDeviceEntryPoint.Callback = object : LinkNewDeviceEntryPoint.Callback {
            override fun onDone() = lambdaError()
        }
        val result = entryPoint.createNode(parentNode, BuildContext.root(null), callback)
        assertThat(result).isInstanceOf(LinkNewDeviceFlowNode::class.java)
    }

    /**
     * GUA FORK: ADM-009 decision 5 ships behind the account-authority flag, and this is the gate.
     *
     * With the flag off the flow never asks whether there is a device to grant, so a linked device reaches
     * exactly the screen it reached before this feature existed.
     */
    @Test
    fun `no grant is offered while the account-authority flag is off`() = runTest {
        val client = FakeMatrixClient()
        val keySource = RecordingLinkedDeviceAuthorityKeySource()
        val node = LinkNewDeviceFlowNode(
            buildContext = BuildContext.root(null),
            plugins = listOf(
                object : LinkNewDeviceEntryPoint.Callback {
                    override fun onDone() = lambdaError()
                }
            ),
            sessionCoroutineScope = backgroundScope,
            linkNewMobileHandler = LinkNewMobileHandler(client),
            linkNewDesktopHandler = LinkNewDesktopHandler(client),
            sessionEnterpriseService = FakeSessionEnterpriseService(),
            sessionId = A_SESSION_ID,
            featureFlagService = FakeFeatureFlagService(),
            sessionStore = InMemorySessionStore(),
            authorityManager = FakeAccountAuthorityManager(),
            linkedDeviceAuthorityKeySource = keySource,
        )

        assertThat(node.grantCandidate()).isNull()
        assertThat(keySource.calls).isEmpty()
    }
}

/** Records whether the flow asked for a candidate at all, which with the flag off it must not. */
private class RecordingLinkedDeviceAuthorityKeySource : LinkedDeviceAuthorityKeySource {
    val calls: MutableList<String> = mutableListOf()

    override suspend fun candidate(accessToken: String): DeviceGrantCandidate? {
        calls += accessToken
        return DeviceGrantCandidate(deviceKeyB64Url = "a-key", label = "Pixel Tablet")
    }
}
