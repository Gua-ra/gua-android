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
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.features.linknewdevice.impl.screens.grantauthority.aCandidate
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
                // GUA FORK: ADM-009. Off by default, so the flow ends where it always ended.
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

    /**
     * GUA FORK: ADM-009 decision 5 ships behind the account-authority flag, and this is the gate.
     *
     * With the flag off the flow never asks whether there is a device to grant, so a linked device reaches
     * exactly the screen it reached before this feature existed.
     */
    @Test
    fun `no grant is offered while the account-authority flag is off`() = runTest {
        val authorityManager = FakeAccountAuthorityManager(
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val node = createFlowNode(authorityManager = authorityManager)

        assertThat(node.grantCandidate()).isNull()
        // Not even asked: with the flag off this flow does not talk to the authority endpoints at all.
        assertThat(authorityManager.candidatesCalls).isEmpty()
    }

    /**
     * GUA FORK: ADM-009 decision 5. The grant offer stands on the ceremony's own outcome, and the ceremony has
     * not confirmed anything in a freshly built flow.
     *
     * This is the check that used to be a `validate()` returning true. There is no local pre-check in the
     * pinned SDK, so the signal is the step the SDK emits only after its confirm step succeeded, and a flow
     * that never reached it can offer nothing whatever else is in place.
     */
    @Test
    fun `no grant is offered on a ceremony whose check code was never confirmed`() = runTest {
        val authorityManager = FakeAccountAuthorityManager(
            candidatesResult = { Result.success(listOf(aCandidate())) },
        )
        val node = createFlowNode(
            authorityManager = authorityManager,
            // Everything else in place: the feature is on, and this phone holds authority.
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
