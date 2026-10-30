/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryEvents
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryNode
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryPresenter
import io.element.android.features.login.impl.screens.phoneentry.PhoneEntryState
import io.element.android.features.login.impl.web.FakeWebClientUrlForAuthenticationRetriever
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.test.auth.AN_OAUTH_DATA
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.oauth.test.customtab.FakeOAuthActionFlow
import io.element.android.libraries.phonenumberentry.FakeDeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginHelperResolverVerificationTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `the verifier sees the number and the resolution the sign-in used`() = runTest {
        val verifier = FakeResolverVerifier()
        val homeserverRecorder = lambdaRecorder<String, Result<MatrixHomeServerDetails>> {
            Result.success(aMatrixHomeServerDetails(supportsOAuthLogin = true))
        }
        val presenter = createPhoneEntryPresenter(verifier = verifier, setHomeserverResult = homeserverRecorder)

        val state = submitNumber(presenter)

        assertThat(state.loginMode).isInstanceOf(AsyncData.Success::class.java)
        homeserverRecorder.assertions().isCalledOnce().with(value(A_RESOLUTION.homeserver.baseUrl))
        assertThat(verifier.verifyCalls).containsExactly(A_PHONE_NUMBER to A_RESOLUTION)
    }

    @Test
    fun `the sign-in completes before the verification ends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val verifier = FakeResolverVerifier(
            verifyResult = { _, _ ->
                gate.await()
                aResolverVerificationReport()
            },
        )
        val presenter = createPhoneEntryPresenter(verifier = verifier)

        val state = submitNumber(presenter)

        assertThat(state.loginMode).isInstanceOf(AsyncData.Success::class.java)
        assertThat(verifier.verifyCalls).hasSize(1)
        assertThat(verifier.completedCount).isEqualTo(0)
        gate.complete(Unit)
        assertThat(verifier.completedCount).isEqualTo(1)
    }

    @Test
    fun `with the flag off the verifier is not invoked and the sign-in is unchanged`() = runTest {
        val verifier = FakeResolverVerifier(verifyResult = { _, _ -> error("the verifier must not run while the flag is off") })
        val presenter = createPhoneEntryPresenter(verifier = verifier, verificationEnabled = false)

        val state = submitNumber(presenter)

        assertThat(state.loginMode).isInstanceOf(AsyncData.Success::class.java)
        assertThat(verifier.verifyCalls).isEmpty()
    }

    @Test
    fun `an Error thrown by the verifier does not stop the sign-in`() = runTest {
        val verifier = FakeResolverVerifier(verifyResult = { _, _ -> throw NoSuchFieldError("TWO") })
        val presenter = createPhoneEntryPresenter(verifier = verifier)

        val state = submitNumber(presenter)

        assertThat(state.loginMode).isInstanceOf(AsyncData.Success::class.java)
        assertThat(verifier.verifyCalls).hasSize(1)
    }

    private suspend fun submitNumber(presenter: PhoneEntryPresenter): PhoneEntryState {
        var terminal: PhoneEntryState? = null
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged(A_LOCAL_NUMBER))
            val typedState = awaitItem()
            typedState.eventSink(PhoneEntryEvents.Continue)
            while (true) {
                val state = awaitItem()
                if (state.loginMode is AsyncData.Success || state.loginMode is AsyncData.Failure) {
                    terminal = state
                    break
                }
            }
        }
        return checkNotNull(terminal)
    }

    private fun createPhoneEntryPresenter(
        verifier: FakeResolverVerifier,
        verificationEnabled: Boolean = true,
        setHomeserverResult: (String) -> Result<MatrixHomeServerDetails> = {
            Result.success(aMatrixHomeServerDetails(supportsOAuthLogin = true))
        },
    ): PhoneEntryPresenter {
        val loginHelper = LoginHelper(
            oAuthActionFlow = FakeOAuthActionFlow(),
            authenticationService = FakeMatrixAuthenticationService(
                setHomeserverResult = setHomeserverResult,
                getOAuthUrlResult = { _, _ -> Result.success(AN_OAUTH_DATA) },
            ),
            webClientUrlForAuthenticationRetriever = FakeWebClientUrlForAuthenticationRetriever(),
            resolverClient = FakeResolverClient(resolveResult = { Result.success(A_RESOLUTION) }),
            featureFlagService = FakeFeatureFlagService(
                initialState = mapOf(FeatureFlags.ResolverVerification.key to verificationEnabled),
            ),
            accountGenesisManager = FakeAccountGenesisManager(),
            resolverVerifier = verifier,
            appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            deployment = FakeGuaDeployment(),
        )
        return PhoneEntryPresenter(
            params = PhoneEntryNode.Params(initialPhoneNumber = null),
            loginHelper = loginHelper,
            selectedCountryStore = SelectedCountryStore(),
            deviceCountryProvider = FakeDeviceCountryProvider(),
        )
    }

    private companion object {
        private const val A_LOCAL_NUMBER = "2015550123"
        private const val A_PHONE_NUMBER = "+12015550123"
        private val A_RESOLUTION = HomeserverResolution(
            exists = false,
            homeserver = ResolvedHomeserver(
                serverName = "gua.global",
                baseUrl = "https://matrix.gua.global",
                masIssuer = "https://mas.gua.global",
                region = "br",
            ),
        )
    }
}
