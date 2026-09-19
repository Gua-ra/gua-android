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
import io.element.android.features.login.impl.screens.chooseaccountprovider.ChooseAccountProviderPresenter
import io.element.android.features.login.impl.screens.confirmaccountprovider.ConfirmAccountProviderPresenter
import io.element.android.features.login.impl.screens.createaccount.AccountCreationNotSupported
import io.element.android.features.login.impl.screens.onboarding.OnBoardingPresenter
import io.element.android.features.login.impl.web.WebClientUrlForAuthenticationRetriever
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.GuaDeployment
import io.element.android.libraries.guaresolver.GuaResolverConfig
import io.element.android.libraries.guaresolver.ResolverClient
import io.element.android.libraries.guaresolver.genesis.AccountGenesisManager
import io.element.android.libraries.guaresolver.genesis.GenesisRegistration
import io.element.android.libraries.guaresolver.genesis.GuaLoginHint
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.OAuthPrompt
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthActionFlow

/**
 * This class is responsible for managing the login flow, including handling OIDC actions and
 * submitting login requests.
 * It's a helper to avoid code duplication. It is used by [OnBoardingPresenter], [ConfirmAccountProviderPresenter]
 * and [ChooseAccountProviderPresenter].
 */
@Inject
class LoginHelper(
    private val oAuthActionFlow: OAuthActionFlow,
    private val authenticationService: MatrixAuthenticationService,
    private val webClientUrlForAuthenticationRetriever: WebClientUrlForAuthenticationRetriever,
    private val resolverClient: ResolverClient,
    private val featureFlagService: FeatureFlagService,
    private val accountGenesisManager: AccountGenesisManager,
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

    suspend fun submit(
        isAccountCreation: Boolean,
        homeserverUrl: String,
        resolvedHomeserverUrl: String?,
        loginHint: String?,
    ) {
        suspend {
            authenticationService.setHomeserver(homeserverUrl).recoverCatching {
                // No .well-known file?
                // If the homeserver is not reachable, try using resolvedHomeserverUrl.
                if (resolvedHomeserverUrl != null && resolvedHomeserverUrl != homeserverUrl) {
                    authenticationService.setHomeserver(resolvedHomeserverUrl).getOrThrow()
                } else {
                    throw it
                }
            }.map { matrixHomeServerDetails ->
                if (matrixHomeServerDetails.supportsOAuthLogin) {
                    // Retrieve the details right now
                    val oAuthPrompt = if (isAccountCreation) OAuthPrompt.Create else OAuthPrompt.Login
                    LoginMode.OAuth(
                        authenticationService.getOAuthUrl(prompt = oAuthPrompt, loginHint = loginHint).getOrThrow()
                    )
                } else if (isAccountCreation) {
                    val url = webClientUrlForAuthenticationRetriever.retrieve(homeserverUrl)
                    LoginMode.AccountCreation(url)
                } else if (matrixHomeServerDetails.supportsPasswordLogin) {
                    LoginMode.PasswordLogin
                } else {
                    error("Unsupported login flow")
                }
            }.getOrThrow()
        }.runCatchingUpdatingState(
            state = loginModeState,
            errorTransform = {
                when (it) {
                    is AccountCreationNotSupported -> it
                    else -> ChangeServerError.from(it)
                }
            }
        )
    }

    /**
     * GUA FORK: phone-first entry. Resolves the E.164 phone number to its homeserver via the Gua
     * resolver, configures the auth service for that homeserver, then builds the MAS OIDC url with the
     * phone as the OIDC `login_hint` — mirroring iOS `AuthenticationFlowCoordinator.handlePhoneSubmission`
     * (resolve -> configure -> urlForOIDCLogin -> continueWithOIDC). The whole pipeline runs as one
     * [loginModeState] Loading -> Success/Failure cycle; the resulting [LoginMode.OAuth] is handed to the
     * navigator (Custom Tab) by the screen, exactly like the legacy account-provider path.
     *
     * The resolver decides login vs register; the homeserver base URL it returns is never surfaced in UI.
     */
    suspend fun submitPhone(e164Phone: String) {
        suspend {
            val resolution = resolverClient.resolve(e164Phone).getOrThrow()
            val homeserverUrl = resolution.homeserver.baseUrl
            val isAccountCreation = !resolution.exists
            // GUA FORK: ADM-008 Phase 3. A brand-new account registers its on-device genesis BEFORE the
            // OIDC flow starts and carries the handle in the reserved login_hint grammar. With the
            // feature flag off this is exactly the bare E.164 hint it has always been.
            val loginHint = loginHintFor(e164Phone = e164Phone, isAccountCreation = isAccountCreation)
            // Configure the auth service for the resolved homeserver, then build the OIDC url.
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
                // A genesis the device meant to register and could not must reach the user as itself,
                // not as a generic server error, because it is the one case that stops the signup.
                if (it is AccountGenesisSignupError) it else ChangeServerError.from(it)
            }
        )
    }

    /**
     * GUA FORK: the OIDC `login_hint` for a phone submission.
     *
     * Returns the bare E.164 number, exactly as before account genesis existed, unless the feature flag
     * is on AND this is a brand-new account AND the deployment issued a handle. A returning user is
     * never given a genesis here: their account already has an accountId, and registering another would
     * be an attempt to re-point it.
     *
     * @throws AccountGenesisSignupError.SetupFailed when the device meant to register a genesis and
     * could not, so the signup stops instead of silently creating an account without one.
     */
    private suspend fun loginHintFor(e164Phone: String, isAccountCreation: Boolean): String {
        if (!featureFlagService.isFeatureEnabled(FeatureFlags.AccountGenesis)) return e164Phone
        if (!isAccountCreation) return e164Phone
        return when (val registration = accountGenesisManager.registerForSignup()) {
            is GenesisRegistration.Registered -> GuaLoginHint.forPhone(e164Phone, registration.attachHandle)
            // The deployment does not do genesis. Continue with today's signup, showing nothing.
            GenesisRegistration.Unavailable -> e164Phone
            is GenesisRegistration.Failed -> throw AccountGenesisSignupError.SetupFailed
        }
    }

    /**
     * GUA FORK: sign in with a passkey. Mirrors iOS `AuthenticationFlowCoordinator.handlePasskeySignIn`.
     *
     * A passkey is a discoverable credential: it was registered with a resident key and the assertion
     * carries neither a username nor an allow list, so the credential identifies the account by itself.
     * Nothing about signing in this way needs a phone number, so the resolver is skipped for the same
     * reason it cannot be used: it maps a number to a homeserver, and there is no number here. The
     * deployment's default account provider is configured instead (the first configured provider, as
     * on iOS), so an account that lives elsewhere still signs in by number.
     *
     * The OIDC request keeps `prompt=login` and sends the reserved [PASSKEY_LOGIN_HINT] as the
     * `login_hint`. MAS forwards the hint verbatim; identity-service maps it to a PASSKEY session
     * intent so the sign-in page leads with the passkey instead of auto-submitting a number and
     * sending a code. An older identity-service that does not know the hint maps it to no phone
     * hint, which is today's behaviour, so nothing regresses if the app ships first.
     *
     * Fails closed with [PasskeySignInError.NotConfigured] when the deployment has no default account
     * provider, the way the phone path fails with `ResolverError.NotConfigured`.
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
            errorTransform = { ChangeServerError.from(it) }
        )
    }

    private suspend fun onOAuthAction(oAuthAction: OAuthAction) {
        // GUA FORK: the identity-reset return is for the reset flow, not for login.
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
        /**
         * GUA FORK: the reserved OIDC `login_hint` value that asks the sign-in page to lead with a
         * passkey. Defined once here; the identity-service maps this literal to its PASSKEY intent.
         */
        const val PASSKEY_LOGIN_HINT = "passkey"
    }
}
