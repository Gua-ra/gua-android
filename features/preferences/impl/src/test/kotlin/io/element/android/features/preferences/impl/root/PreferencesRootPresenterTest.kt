/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.preferences.impl.root

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.enterprise.test.FakeSessionEnterpriseService
import io.element.android.features.lockscreen.test.FakeLockScreenService
import io.element.android.features.logout.api.direct.aDirectLogoutState
import io.element.android.features.preferences.impl.fixtures.FakeIdentityServiceClient
import io.element.android.features.preferences.impl.fixtures.aFactorStatus
import io.element.android.features.preferences.impl.utils.ShowDeveloperSettingsProvider
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeature
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.indicator.test.FakeIndicatorService
import io.element.android.libraries.matrix.api.oauth.AccountManagementAction
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.AN_AVATAR_URL
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PreferencesRootPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private companion object {
        const val MAX_EMISSIONS = 10
    }

    @Test
    fun `present - initial state`() = runTest {
        val accountManagementUrlResult = lambdaRecorder<AccountManagementAction?, Result<String?>> { action ->
            Result.success("$action url")
        }
        val matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = accountManagementUrlResult,
        )
        createPresenter(
            matrixClient = matrixClient,
            sessionEnterpriseService = FakeSessionEnterpriseService(
                tweakMasUrlResult = { "tweaked $it" },
            ),
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.myUser).isEqualTo(
                MatrixUser(
                    userId = matrixClient.sessionId,
                    displayName = A_USER_NAME,
                    avatarUrl = AN_AVATAR_URL
                )
            )
            assertThat(initialState.version).isEqualTo("A Version")
            assertThat(initialState.isMultiAccountEnabled).isFalse()
            assertThat(initialState.otherSessions).isEmpty()
            assertThat(initialState.version).isEqualTo("A Version")
            val loadedState = awaitItem()
            assertThat(loadedState.myUser).isEqualTo(
                MatrixUser(
                    userId = matrixClient.sessionId,
                    displayName = A_USER_NAME,
                    avatarUrl = AN_AVATAR_URL
                )
            )
            // GUA FORK: Encryption is always reachable now. Upstream hid it whenever the session
            // still needed verifying, but Gua never presents that ceremony, so the row would have
            // been hidden forever, taking recovery-key entry with it.
            // GUA FORK: the Encryption screen is upstream's recovery-key console, so it now
            // tracks developer settings, which this default (debug) fixture has on. The
            // user-facing case is covered by `the encryption screen is hidden from users`.
            assertThat(loadedState.showSecureBackup).isTrue()
            assertThat(loadedState.showSecureBackupBadge).isFalse()
            assertThat(loadedState.accountManagementUrl).isNull()
            assertThat(loadedState.showAnalyticsSettings).isFalse()
            assertThat(loadedState.showLinkNewDevice).isFalse()
            assertThat(loadedState.showDeveloperSettings).isTrue()
            assertThat(loadedState.canDeactivateAccount).isTrue()
            assertThat(loadedState.canReportBug).isTrue()
            assertThat(loadedState.nbOfBlockedUsers).isEqualTo(0)
            assertThat(loadedState.directLogoutState).isEqualTo(aDirectLogoutState())
            assertThat(loadedState.snackbarMessage).isNull()
            val finalState = awaitItem()
            accountManagementUrlResult.assertions().isCalledOnce()
                .with(value(null))
            // GUA FORK: the shared browser tab can hold another account's session, so the URL names
            // this account for the page to refuse any other.
            assertThat(finalState.accountManagementUrl)
                .isEqualTo("tweaked null url?org.matrix.msc4198.login_hint=mxid%3A%40alice%3Aserver.org")
        }
    }

    @Test
    fun `present - cannot report bug`() = runTest {
        val matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = { Result.success("") },
        )
        createPresenter(
            matrixClient = matrixClient,
            rageshakeFeatureAvailability = { flowOf(false) },
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.canReportBug).isFalse()
            skipItems(1)
        }
    }

    @Test
    fun `present - number of blocked users`() = runTest {
        val matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = { Result.success("") },
            ignoredUsersFlow = MutableStateFlow(persistentListOf(A_USER_ID, A_USER_ID_2)),
        )
        createPresenter(
            matrixClient = matrixClient,
        ).test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.nbOfBlockedUsers).isEqualTo(2)
        }
    }

    @Test
    fun `present - secure backup badge`() = runTest {
        val matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = { Result.success("") },
        )
        val indicatorService = FakeIndicatorService()
        createPresenter(
            matrixClient = matrixClient,
            rageshakeFeatureAvailability = { flowOf(false) },
            indicatorService = indicatorService,
        ).test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.showSecureBackupBadge).isFalse()
            indicatorService.setShowSettingChatBackupIndicator(true)
            val finalState = awaitItem()
            assertThat(finalState.showSecureBackupBadge).isTrue()
        }
    }

    @Test
    fun `present - can deactivate account is false if the Matrix client say so`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { false },
                accountManagementUrlResult = { Result.success(null) },
            ),
        ).test {
            val loadedState = awaitFirstItem()
            assertThat(loadedState.canDeactivateAccount).isFalse()
        }
    }

    @Test
    fun `present - the encryption screen is hidden from users and shown to developers`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
            showDeveloperSettingsProvider = ShowDeveloperSettingsProvider(aBuildMeta(BuildType.RELEASE)),
            buildMeta = aBuildMeta(BuildType.RELEASE),
        ).test {
            // GUA FORK: gated on build TYPE, not the seven-tap developer unlock. That unlock
            // works in any build, so gating on it would still let a release user reach the
            // recovery-key console.
            assertThat(awaitFirstItem().showSecureBackup).isFalse()
        }
    }

    @Test
    fun `present - developer settings is hidden by default in release builds`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
            showDeveloperSettingsProvider = ShowDeveloperSettingsProvider(aBuildMeta(BuildType.RELEASE))
        ).test {
            val loadedState = awaitFirstItem()
            assertThat(loadedState.showDeveloperSettings).isFalse()
        }
    }

    @Test
    fun `present - developer settings can be enabled in release builds`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
            showDeveloperSettingsProvider = ShowDeveloperSettingsProvider(aBuildMeta(BuildType.RELEASE))
        ).test {
            val loadedState = awaitFirstItem()
            repeat(times = ShowDeveloperSettingsProvider.DEVELOPER_SETTINGS_COUNTER) {
                assertThat(loadedState.showDeveloperSettings).isFalse()
                loadedState.eventSink(PreferencesRootEvent.OnVersionInfoClick)
            }
            assertThat(awaitItem().showDeveloperSettings).isTrue()
        }
    }

    @Test
    fun `present - switch session invoke method on the session store`() = runTest {
        val setLatestSessionResult = lambdaRecorder<String, Unit> { }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(sessionId = A_SESSION_ID.value),
                aSessionData(sessionId = A_SESSION_ID_2.value),
            ),
            setLatestSessionResult = setLatestSessionResult,
        )
        createPresenter(
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
            sessionStore = sessionStore,
        ).test {
            val loadedState = awaitFirstItem()
            loadedState.eventSink(PreferencesRootEvent.SwitchToSession(A_SESSION_ID_2))
            setLatestSessionResult.assertions().isCalledOnce()
                .with(value(A_SESSION_ID_2.value))
        }
    }

    @Test
    fun `present - labs can be shown if any feature flag is in labs and not finished`() = runTest {
        createPresenter(
            featureFlagService = FakeFeatureFlagService(
                getAvailableFeaturesResult = { _, _ ->
                    listOf(
                        FakeFeature(
                            key = "feature_1",
                            title = "Feature 1",
                            isInLabs = true,
                            isFinished = false,
                        )
                    )
                }
            ),
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
        ).test {
            assertThat(awaitItem().showLabsItem).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - labs can't be shown if all feature flags in labs are finished`() = runTest {
        createPresenter(
            featureFlagService = FakeFeatureFlagService(
                getAvailableFeaturesResult = { _, _ ->
                    emptyList()
                }
            ),
            matrixClient = FakeMatrixClient(
                canDeactivateAccountResult = { true },
                accountManagementUrlResult = { Result.success(null) },
            ),
        ).test {
            skipItems(1)
            assertThat(awaitItem().showLabsItem).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - multiple accounts`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                sessionId = A_SESSION_ID,
                canDeactivateAccountResult = { true },
            ),
            featureFlagService = FakeFeatureFlagService(
                initialState = mapOf(FeatureFlags.MultiAccount.key to true)
            ),
            sessionStore = InMemorySessionStore(
                initialList = listOf(
                    aSessionData(sessionId = A_SESSION_ID.value),
                    aSessionData(
                        sessionId = A_SESSION_ID_2.value,
                        userDisplayName = "Bob",
                        userAvatarUrl = "avatarUrl",
                    ),
                )
            )
        ).test {
            val state = awaitFirstItem()
            assertThat(state.isMultiAccountEnabled).isTrue()
            assertThat(state.otherSessions).hasSize(1)
            assertThat(state.otherSessions[0]).isEqualTo(MatrixUser(userId = A_SESSION_ID_2, displayName = "Bob", avatarUrl = "avatarUrl"))
        }
    }

    @Test
    fun `present - link new device`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(
                sessionId = A_SESSION_ID,
                canDeactivateAccountResult = { true },
            ),
            featureFlagService = FakeFeatureFlagService(
                initialState = mapOf(FeatureFlags.QrCodeLogin.key to true)
            ),
        ).test {
            val state = awaitFirstItem()
            assertThat(state.showLinkNewDevice).isTrue()
        }
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitFirstItem(): T {
        skipItems(1)
        return awaitItem()
    }

    // GUA FORK: the two-step-verification nudge is gated on the account's FACTORS. It used to start
    // at false with no failure handler, so a passkey holder, and anyone whose status read was slow
    // or failed, was told to set up a PIN they did not need.

    @Test
    fun `present - the nudge is shown to an account with no strong factor`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(canDeactivateAccountResult = { false }),
            sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value))),
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = false)) },
            ),
        ).test {
            val state = awaitFactorStatus { it == false }
            assertThat(state).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a passkey holder with no PIN is not nudged to set up a PIN`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(canDeactivateAccountResult = { false }),
            sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value))),
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.success(aFactorStatus(hasPin = false, passkeyRegistered = true)) },
            ),
        ).test {
            val state = awaitFactorStatus { it == true }
            assertThat(state).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a factor status that cannot be read leaves the nudge hidden`() = runTest {
        createPresenter(
            matrixClient = FakeMatrixClient(canDeactivateAccountResult = { false }),
            sessionStore = InMemorySessionStore(listOf(aSessionData(sessionId = A_SESSION_ID.value))),
            identityServiceClient = FakeIdentityServiceClient(
                factorStatusResult = { Result.failure(ResolverError.Transport(RuntimeException("offline"))) },
            ),
        ).test {
            // Unknown stays null: nothing writes a value on the failure path, and the View only
            // shows the banner on an explicit false, so nobody is nagged on a failed read.
            assertThat(awaitItem().hasAccountStrongFactor).isNull()
            assertThat(awaitItem().hasAccountStrongFactor).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<PreferencesRootState>.awaitFactorStatus(
        predicate: (Boolean?) -> Boolean,
    ): Boolean? {
        repeat(MAX_EMISSIONS) {
            val value = awaitItem().hasAccountStrongFactor
            if (predicate(value)) return value
        }
        error("No matching factor status after $MAX_EMISSIONS emissions")
    }

    private fun createPresenter(
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        showDeveloperSettingsProvider: ShowDeveloperSettingsProvider = ShowDeveloperSettingsProvider(aBuildMeta(BuildType.DEBUG)),
        rageshakeFeatureAvailability: RageshakeFeatureAvailability = RageshakeFeatureAvailability { flowOf(true) },
        indicatorService: IndicatorService = FakeIndicatorService(),
        featureFlagService: FeatureFlagService = FakeFeatureFlagService(),
        sessionStore: SessionStore = InMemorySessionStore(),
        sessionEnterpriseService: SessionEnterpriseService = FakeSessionEnterpriseService(),
        lockScreenService: FakeLockScreenService = FakeLockScreenService().apply { setIsPinSetup(true) },
        identityServiceClient: IdentityServiceClient = FakeIdentityServiceClient(
            factorStatusResult = { Result.success(aFactorStatus(hasPin = false)) },
        ),
        buildMeta: BuildMeta = aBuildMeta(),
    ) = PreferencesRootPresenter(
        matrixClient = matrixClient,
        analyticsService = FakeAnalyticsService(),
        versionFormatter = FakeVersionFormatter(),
        snackbarDispatcher = SnackbarDispatcher(),
        indicatorService = indicatorService,
        directLogoutPresenter = { aDirectLogoutState() },
        showDeveloperSettingsProvider = showDeveloperSettingsProvider,
        rageshakeFeatureAvailability = rageshakeFeatureAvailability,
        featureFlagService = featureFlagService,
        sessionStore = sessionStore,
        sessionEnterpriseService = sessionEnterpriseService,
        lockScreenService = lockScreenService,
        identityServiceClient = identityServiceClient,
        buildMeta = buildMeta,
    )
}
