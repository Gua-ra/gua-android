/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.Inject
import io.element.android.features.login.impl.error.ChangeServerError
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolverClient
import io.element.android.libraries.guaresolver.genesis.AccountGenesisManager
import io.element.android.libraries.guaresolver.genesis.GenesisRegistration
import io.element.android.libraries.guaresolver.genesis.GuaLoginHint
import io.element.android.libraries.guaresolver.verification.ResolverVerifier
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.OAuthPrompt
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/** GUA FORK: phone and passkey sign-in; the account provider screens use [LoginModePresenter]. */
@Inject
class LoginHelper(
    private val oAuthActionFlow: OAuthActionFlow,
    private val authenticationService: MatrixAuthenticationService,
    private val resolverClient: ResolverClient,
    private val featureFlagService: FeatureFlagService,
    private val accountGenesisManager: AccountGenesisManager,
    private val resolverVerifier: ResolverVerifier,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val deployment: GuaDeployment = GuaResolverConfig.current,
) {
    private val loginModeState: MutableState<AsyncData<LoginMode>> = mutableStateOf(AsyncData.Uninitialized)

    @Composable
    fun collectLoginMode(): State<AsyncData<LoginMode>> {
        LaunchedEffect(Unit) {
            oAuthActionFlow.collect { oAuthAction ->
                if (oAuthAction != null) {
                    onOAuthAction(oAuthAction)
                }
            }
        }
        return loginModeState
    }

    fun clearError() {
        loginModeState.value = AsyncData.Uninitialized
    }

    suspend fun submitPhone(e164Phone: String) {
        suspend {
            val resolution = resolverClient.resolve(e164Phone).getOrThrow()
            appCoroutineScope.launch { verifyResolution(e164Phone, resolution) }
            val homeserverUrl = resolution.homeserver.baseUrl
            val isAccountCreation = !resolution.exists
            val loginHint = loginHintFor(e164Phone = e164Phone, isAccountCreation = isAccountCreation)
            authenticationService.setHomeserver(homeserverUrl)
                .map { matrixHomeServerDetails ->
                    if (matrixHomeServerDetails.supportsOAuthLogin) {
                        val oAuthPrompt = if (isAccountCreation) OAuthPrompt.Create else OAuthPrompt.Login
                        LoginMode.OAuth(
                            authenticationService.getOAuthUrl(prompt = oAuthPrompt, loginHint = loginHint).getOrThrow()
                        )
                    } else {
                        error("Unsupported login flow")
                    }
                }
                .getOrThrow()
        }.runCatchingUpdatingState(
            state = loginModeState,
            errorTransform = {
                // Left unmapped: it is the one failure that stops the signup.
                if (it is AccountGenesisSignupError) it else signInError(it)
            }
        )
    }

    /**
     * A returning user gets no genesis: their account already has an accountId, and registering another would re-point it.
     *
     * @throws AccountGenesisSignupError.SetupFailed when a genesis could not be registered, so no account is created without one.
     */
    private suspend fun loginHintFor(e164Phone: String, isAccountCreation: Boolean): String {
        if (!featureFlagService.isFeatureEnabled(FeatureFlags.AccountGenesis)) return e164Phone
        if (!isAccountCreation) return e164Phone
        return when (val registration = accountGenesisManager.registerForSignup()) {
            is GenesisRegistration.Registered -> GuaLoginHint.forPhone(e164Phone, registration.attachHandle)
            GenesisRegistration.Unavailable -> e164Phone
            is GenesisRegistration.Failed -> throw AccountGenesisSignupError.SetupFailed
        }
    }

    /** Report-only verification of the resolver's answer, without affecting sign-in. */
    private suspend fun verifyResolution(e164Phone: String, resolution: HomeserverResolution) {
        try {
            if (featureFlagService.isFeatureEnabled(FeatureFlags.ResolverVerification)) {
                resolverVerifier.verify(e164Phone, resolution)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Errors too: nothing above this job handles them, so one that escaped would end the app.
            Timber.w("Resolver verification did not complete")
        }
    }

    /**
     * Without a number the resolver cannot be used, so the default account provider is configured; accounts elsewhere sign in by number.
     * An identity-service that does not know [PASSKEY_LOGIN_HINT] treats it as no hint, so the app can ship first.
     */
    suspend fun submitPasskey() {
        suspend {
            val accountProvider = deployment.defaultAccountProvider?.takeIf { it.isNotEmpty() }
                ?: throw PasskeySignInError.NotConfigured
            authenticationService.setHomeserver(accountProvider)
                .map { matrixHomeServerDetails ->
                    if (matrixHomeServerDetails.supportsOAuthLogin) {
                        LoginMode.OAuth(
                            authenticationService.getOAuthUrl(prompt = OAuthPrompt.Login, loginHint = PASSKEY_LOGIN_HINT).getOrThrow()
                        )
                    } else {
                        throw ChangeServerError.UnsupportedServer
                    }
                }
                .getOrThrow()
        }.runCatchingUpdatingState(
            state = loginModeState,
            errorTransform = { signInError(it) }
        )
    }

    /** The user never typed a server address on these paths, so an unreachable server is reported as a connection problem. */
    private fun signInError(error: Throwable): ChangeServerError = when (val mapped = ChangeServerError.from(error)) {
        ChangeServerError.InvalidServer -> ChangeServerError.Error(messageId = CommonStrings.error_network_or_server_issue)
        else -> mapped
    }

    private suspend fun onOAuthAction(oAuthAction: OAuthAction) {
        // GUA FORK: an identity-reset return belongs to the reset flow.
        if (oAuthAction is OAuthAction.IdentityResetApproved) return
        if (oAuthAction is OAuthAction.GoBack && oAuthAction.toUnblock && loginModeState.value !is AsyncData.Loading) {
            // Ignore GoBack action if the current state is not Loading. This GoBack action is coming from LoginFlowNode.
            // This can happen if there is an error, for instance attempt to login again on the same account.
            return
        }
        loginModeState.value = AsyncData.Loading()
        when (oAuthAction) {
            is OAuthAction.GoBack -> {
                authenticationService.cancelOAuthLogin()
                    .onSuccess {
                        loginModeState.value = AsyncData.Uninitialized
                    }
                    .onFailure { failure ->
                        loginModeState.value = AsyncData.Failure(failure)
                    }
            }
            is OAuthAction.Success -> {
                authenticationService.loginWithOAuth(oAuthAction.url)
                    .onFailure { failure ->
                        loginModeState.value = AsyncData.Failure(failure)
                    }
            }
            is OAuthAction.IdentityResetApproved -> Unit
        }
        oAuthActionFlow.reset()
    }

    companion object {
        /** Reserved `login_hint` that identity-service maps to its PASSKEY intent, so the sign-in page leads with a passkey. */
        const val PASSKEY_LOGIN_HINT = "passkey"
    }
}
