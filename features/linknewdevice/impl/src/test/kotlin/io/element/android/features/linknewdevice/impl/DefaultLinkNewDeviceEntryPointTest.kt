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
import io.element.android.features.linknewdevice.impl.screens.grantauthority.aCandidate
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import kotlinx.coroutines.test.TestScope
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
                featureFlagService = FakeFeatureFlagService(),
                sessionStore = InMemorySessionStore(),
                authorityManager = FakeAccountAuthorityManager(),
            )
        }
        val callback: LinkNewDeviceEntryPoint.Callback = object : LinkNewDeviceEntryPoint.Callback {
            override fun onDone() = lambdaError()
        }
        val result = entryPoint.createNode(parentNode, BuildContext.root(null), callback)
        assertThat(result).isInstanceOf(LinkNewDeviceFlowNode::class.java)
    }

    @Test
    fun `no grant is offered while the account-authority flag is off`() = runTest {
        val authorityManager = FakeAccountAuthorityManager(
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val node = createFlowNode(authorityManager = authorityManager)

        assertThat(node.grantCandidate()).isNull()
        assertThat(authorityManager.candidatesCalls).isEmpty()
    }

    @Test
    fun `no grant is offered on a ceremony whose check code was never confirmed`() = runTest {
        val authorityManager = FakeAccountAuthorityManager(
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val node = createFlowNode(
            authorityManager = authorityManager,
            featureFlagService = FakeFeatureFlagService(
                initialState = mapOf(FeatureFlags.AccountAuthority.key to true),
            ),
        )

        assertThat(node.grantCandidate()).isNull()
        assertThat(authorityManager.candidatesCalls).isEmpty()
    }

    private fun TestScope.createFlowNode(
        authorityManager: FakeAccountAuthorityManager,
        featureFlagService: FakeFeatureFlagService = FakeFeatureFlagService(),
    ): LinkNewDeviceFlowNode {
        val client = FakeMatrixClient()
        return LinkNewDeviceFlowNode(
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
            featureFlagService = featureFlagService,
            sessionStore = InMemorySessionStore(),
            authorityManager = authorityManager,
        )
    }
}
