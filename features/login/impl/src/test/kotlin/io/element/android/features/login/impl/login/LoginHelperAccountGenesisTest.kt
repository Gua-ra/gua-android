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
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.genesis.AccountId
import io.element.android.libraries.guaresolver.genesis.GenesisRegistration
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.matrix.api.auth.OAuthPrompt
import io.element.android.libraries.matrix.test.auth.AN_OAUTH_DATA
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.phonenumberentry.FakeDeviceCountryProvider
import io.element.android.libraries.phonenumberentry.SelectedCountryStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK: the account-genesis half of the phone submission (ADM-008 Phase 3).
 *
 * The contract with the most consequence is the flag-off one: while the feature flag is off the
 * `login_hint` must be byte-identical to the bare E.164 number the client has always sent, and nothing
 * in the genesis path may run at all.
 */
class LoginHelperAccountGenesisTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `with the flag off the login hint is the bare number and no genesis is registered`() = runTest {
        val genesisManager = FakeAccountGenesisManager(
            registerResult = { error("the genesis path must not run while the flag is off") },
        )
        val hintRecorder = recordLoginHint()
        val presenter = createPhoneEntryPresenter(
            genesisManager = genesisManager,
            accountGenesisEnabled = false,
            // A brand-new account, which is the only case that would ever register a genesis.
            accountExists = false,
            getOAuthUrlResult = hintRecorder,
        )

        submitNumber(presenter)

        assertThat(capturedHint).isEqualTo(A_PHONE_NUMBER)
        assertThat(genesisManager.registerCount).isEqualTo(0)
    }

    @Test
    fun `a new account with the flag on carries the handle in the reserved grammar`() = runTest {
        val genesisManager = FakeAccountGenesisManager(
            registerResult = {
                GenesisRegistration.Registered(accountId = AccountId.parse(AN_ACCOUNT_ID), attachHandle = A_HANDLE)
            },
        )
        val presenter = createPhoneEntryPresenter(
            genesisManager = genesisManager,
            accountGenesisEnabled = true,
            accountExists = false,
            getOAuthUrlResult = recordLoginHint(),
        )

        submitNumber(presenter)

        assertThat(capturedHint).isEqualTo("gua:phone=$A_PHONE_NUMBER;genesis=$A_HANDLE")
        assertThat(genesisManager.registerCount).isEqualTo(1)
    }

    @Test
    fun `a returning user never registers a genesis, even with the flag on`() = runTest {
        val genesisManager = FakeAccountGenesisManager(
            registerResult = { error("a returning user already has an accountId") },
        )
        val presenter = createPhoneEntryPresenter(
            genesisManager = genesisManager,
            accountGenesisEnabled = true,
            accountExists = true,
            getOAuthUrlResult = recordLoginHint(),
        )

        submitNumber(presenter)

        assertThat(capturedHint).isEqualTo(A_PHONE_NUMBER)
        assertThat(genesisManager.registerCount).isEqualTo(0)
    }

    @Test
    fun `a deployment with no genesis support falls back to today's signup with no error`() = runTest {
        val genesisManager = FakeAccountGenesisManager(registerResult = { GenesisRegistration.Unavailable })
        val presenter = createPhoneEntryPresenter(
            genesisManager = genesisManager,
            accountGenesisEnabled = true,
            accountExists = false,
            getOAuthUrlResult = recordLoginHint(),
        )

        val state = submitNumber(presenter)

        assertThat(capturedHint).isEqualTo(A_PHONE_NUMBER)
        assertThat(genesisManager.registerCount).isEqualTo(1)
        // Silent: the user sees the ordinary OIDC handoff, not an error.
        assertThat(state.loginMode).isInstanceOf(AsyncData.Success::class.java)
    }

    @Test
    fun `a genesis the device meant to register and could not fails the signup`() = runTest {
        val genesisManager = FakeAccountGenesisManager(
            registerResult = { GenesisRegistration.Failed(Exception("the key could not be created")) },
        )
        val oAuthRecorder = lambdaRecorder<OAuthPrompt, String?, Result<OAuthDetails>> { _, _ ->
            error("the OIDC flow must not start without the genesis this signup meant to register")
        }
        val presenter = createPhoneEntryPresenter(
            genesisManager = genesisManager,
            accountGenesisEnabled = true,
            accountExists = false,
            getOAuthUrlResult = oAuthRecorder,
        )

        val state = submitNumber(presenter)

        val error = (state.loginMode as AsyncData.Failure).error
        // Surfaced as itself, so the screen can show the account-setup message rather than a generic one.
        assertThat(error).isEqualTo(AccountGenesisSignupError.SetupFailed)
        oAuthRecorder.assertions().isNeverCalled()
    }

    private var capturedHint: String? = null

    private fun recordLoginHint() = lambdaRecorder<OAuthPrompt, String?, Result<OAuthDetails>> { _, loginHint ->
        capturedHint = loginHint
        Result.success(AN_OAUTH_DATA)
    }

    /** Types a valid number and taps continue, draining to the terminal login mode. */
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
        genesisManager: FakeAccountGenesisManager,
        accountGenesisEnabled: Boolean,
        accountExists: Boolean,
        getOAuthUrlResult: (OAuthPrompt, String?) -> Result<OAuthDetails>,
    ): PhoneEntryPresenter {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = { Result.success(aMatrixHomeServerDetails(supportsOAuthLogin = true)) },
            getOAuthUrlResult = getOAuthUrlResult,
        )
        val loginHelper = LoginHelper(
            oAuthActionFlow = io.element.android.libraries.oauth.test.customtab.FakeOAuthActionFlow(),
            authenticationService = authenticationService,
            webClientUrlForAuthenticationRetriever =
                io.element.android.features.login.impl.web.FakeWebClientUrlForAuthenticationRetriever(),
            resolverClient = FakeResolverClient(
                resolveResult = {
                    Result.success(
                        HomeserverResolution(
                            exists = accountExists,
                            homeserver = ResolvedHomeserver(
                                serverName = "gua.global",
                                baseUrl = "https://matrix.gua.global",
                                masIssuer = "https://mas.gua.global",
                                region = "br",
                            ),
                        )
                    )
                },
            ),
            featureFlagService = FakeFeatureFlagService(
                initialState = mapOf(FeatureFlags.AccountGenesis.key to accountGenesisEnabled),
            ),
            accountGenesisManager = genesisManager,
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
        private const val A_HANDLE = "Zm9vYmFyYmF6cXV1eGNvcmdlZ3JhdWx0"
        private const val AN_ACCOUNT_ID = "ga1aea6aqb5opmzmutench3ggzepkhgwmkajb3epqqrhckkf7bcbcwl2cy"
    }
}
