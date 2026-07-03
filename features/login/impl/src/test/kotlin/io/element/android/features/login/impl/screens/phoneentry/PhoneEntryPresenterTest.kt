/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.phoneentry

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.login.FakeResolverClient
import io.element.android.features.login.impl.login.LoginMode
import io.element.android.features.login.impl.screens.onboarding.createLoginHelper
import io.element.android.features.login.impl.screens.phoneentry.country.SelectedCountryStore
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.guaresolver.HomeserverResolution
import io.element.android.libraries.guaresolver.ResolvedHomeserver
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PhoneEntryPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state seeds device default and is not submittable`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.localPhoneNumber).isEmpty()
            assertThat(initialState.canContinue).isFalse()
            assertThat(initialState.loginMode).isEqualTo(AsyncData.Uninitialized)
        }
    }

    @Test
    fun `present - typing applies national-format masking and validation`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("2015550123"))
            val state = awaitItem()
            // US mask: (201) 555-0123
            assertThat(state.localPhoneNumber).isEqualTo("(201) 555-0123")
            assertThat(state.localDigits).isEqualTo("2015550123")
            assertThat(state.e164PhoneNumber).isEqualTo("+12015550123")
            assertThat(state.canContinue).isTrue()
        }
    }

    @Test
    fun `present - a length-plausible but invalid number keeps continue disabled`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            // 10 digits, so the old length-only heuristic would have accepted it — but the exchange
            // code "123" is not diallable in the NANP, so libphonenumber (and the backend, which
            // runs the same isValidNumber check) rejects it.
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("5551234567"))
            val state = awaitItem()
            assertThat(state.localDigits).isEqualTo("5551234567")
            assertThat(state.canContinue).isFalse()
        }
    }

    @Test
    fun `present - typing a Canadian area code flips the country`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.selectedCountry.isoCode).isEqualTo("US")
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("604"))
            val state = awaitItem()
            assertThat(state.selectedCountry.isoCode).isEqualTo("CA")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - pasting a plus-prefixed number with country code strips the code and enables continue`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.selectedCountry.isoCode).isEqualTo("US")
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("+12015550123"))
            val state = awaitItem()
            assertThat(state.selectedCountry.isoCode).isEqualTo("US")
            assertThat(state.localPhoneNumber).isEqualTo("(201) 555-0123")
            assertThat(state.localDigits).isEqualTo("2015550123")
            assertThat(state.e164PhoneNumber).isEqualTo("+12015550123")
            assertThat(state.canContinue).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - autofilling a number with a redundant country code and no plus strips it`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("12015550123"))
            val state = awaitItem()
            assertThat(state.selectedCountry.isoCode).isEqualTo("US")
            assertThat(state.localPhoneNumber).isEqualTo("(201) 555-0123")
            assertThat(state.localDigits).isEqualTo("2015550123")
            assertThat(state.canContinue).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - pasting a Brazil number with plus switches the country and strips the code`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("+5511912345678"))
            // The country switch (US -> BR) recomposes more than once; drain to the settled frame.
            val state = awaitSettledNonEmpty()
            assertThat(state.selectedCountry.isoCode).isEqualTo("BR")
            assertThat(state.localDigits).isEqualTo("11912345678")
            assertThat(state.localPhoneNumber).isEqualTo("(11) 91234-5678")
            assertThat(state.canContinue).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - pasting a plus-prefixed Canadian number auto-switches to Canada`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.selectedCountry.isoCode).isEqualTo("US")
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("+14165551234"))
            // The country switch (US -> CA) recomposes more than once; drain to the settled frame.
            val state = awaitSettledNonEmpty()
            assertThat(state.selectedCountry.isoCode).isEqualTo("CA")
            assertThat(state.localDigits).isEqualTo("4165551234")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - typing a normal local number is not mis-stripped`() = runTest {
        val presenter = createPhoneEntryPresenter()
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("5551234567"))
            val state = awaitItem()
            assertThat(state.selectedCountry.isoCode).isEqualTo("US")
            assertThat(state.localDigits).isEqualTo("5551234567")
            assertThat(state.localPhoneNumber).isEqualTo("(555) 123-4567")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - continue resolves homeserver then produces an OIDC login mode`() = runTest {
        val resolveRecorder = lambdaRecorder<String, Result<HomeserverResolution>> { phone ->
            assertThat(phone).isEqualTo("+12015550123")
            Result.success(
                HomeserverResolution(
                    exists = true,
                    homeserver = ResolvedHomeserver(
                        serverName = "gua.global",
                        baseUrl = "https://matrix.gua.global",
                        masIssuer = "https://mas.gua.global",
                        region = "us",
                    ),
                )
            )
        }
        val setHomeserverRecorder = lambdaRecorder<String, Result<io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails>> { homeserver ->
            assertThat(homeserver).isEqualTo("https://matrix.gua.global")
            Result.success(aMatrixHomeServerDetails(supportsOAuthLogin = true))
        }
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = setHomeserverRecorder,
        )
        val presenter = createPhoneEntryPresenter(
            loginHelper = createLoginHelper(
                authenticationService = authenticationService,
                resolverClient = FakeResolverClient(resolveResult = resolveRecorder),
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("2015550123"))
            val typedState = awaitItem()
            assertThat(typedState.canContinue).isTrue()
            typedState.eventSink(PhoneEntryEvents.Continue)
            // Drain to the terminal OIDC success state (resolve -> configure -> getOAuthUrl).
            val successState = awaitTerminalLoginMode()
            assertThat(successState.loginMode).isInstanceOf(AsyncData.Success::class.java)
            assertThat(successState.loginMode.dataOrNull()).isInstanceOf(LoginMode.OAuth::class.java)
        }
        resolveRecorder.assertions().isCalledOnce()
        setHomeserverRecorder.assertions().isCalledOnce()
    }

    @Test
    fun `present - resolver failure surfaces an error`() = runTest {
        val presenter = createPhoneEntryPresenter(
            loginHelper = createLoginHelper(
                resolverClient = FakeResolverClient(resolveResult = { Result.failure(ResolverError.NotConfigured) }),
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(PhoneEntryEvents.PhoneNumberChanged("2015550123"))
            val typedState = awaitItem()
            typedState.eventSink(PhoneEntryEvents.Continue)
            val failureState = awaitTerminalLoginMode()
            assertThat(failureState.loginMode).isInstanceOf(AsyncData.Failure::class.java)
        }
    }
}

/**
 * Drains intermediate emissions (typed-state re-emissions + the Loading frame) until the login mode
 * settles into a terminal Success/Failure. The exact intermediate emission ordering is a Molecule
 * recomposition detail; the wiring contract is that the pipeline reaches a terminal state.
 */
private suspend fun app.cash.turbine.ReceiveTurbine<PhoneEntryState>.awaitTerminalLoginMode(): PhoneEntryState {
    while (true) {
        val state = awaitItem()
        if (state.loginMode is AsyncData.Success || state.loginMode is AsyncData.Failure) {
            return state
        }
    }
}

/**
 * Drains intermediate recomposition frames after a country switch until the local number has settled
 * (non-empty). A country change updates two pieces of `rememberSaveable` state, so Molecule may emit a
 * frame with the new country before the formatted number lands; the settled frame is the contract.
 */
private suspend fun app.cash.turbine.ReceiveTurbine<PhoneEntryState>.awaitSettledNonEmpty(): PhoneEntryState {
    while (true) {
        val state = awaitItem()
        if (state.localDigits.isNotEmpty()) {
            return state
        }
    }
}

private fun createPhoneEntryPresenter(
    params: PhoneEntryNode.Params = PhoneEntryNode.Params(initialPhoneNumber = null),
    loginHelper: io.element.android.features.login.impl.login.LoginHelper = createLoginHelper(
        resolverClient = FakeResolverClient(),
    ),
    selectedCountryStore: SelectedCountryStore = SelectedCountryStore(),
) = PhoneEntryPresenter(
    params = params,
    loginHelper = loginHelper,
    selectedCountryStore = selectedCountryStore,
)
