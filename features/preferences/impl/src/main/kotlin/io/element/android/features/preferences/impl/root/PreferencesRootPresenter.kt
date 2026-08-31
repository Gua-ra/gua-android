/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.features.preferences.impl.utils.ShowDeveloperSettingsProvider
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.IdentityServiceClient
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Inject
class PreferencesRootPresenter(
    private val matrixClient: MatrixClient,
    private val analyticsService: AnalyticsService,
    private val versionFormatter: VersionFormatter,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val directLogoutPresenter: Presenter<DirectLogoutState>,
    private val showDeveloperSettingsProvider: ShowDeveloperSettingsProvider,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val featureFlagService: FeatureFlagService,
    private val sessionStore: SessionStore,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val lockScreenService: LockScreenService,
    private val identityServiceClient: IdentityServiceClient,
) : Presenter<PreferencesRootState> {
    @Composable
    override fun present(): PreferencesRootState {
        val coroutineScope = rememberCoroutineScope()
        val matrixUser = matrixClient.userProfile.collectAsState()
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            matrixClient.getUserProfile()
        }

        val isMultiAccountEnabled by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.MultiAccount)
        }.collectAsState(initial = false)
        val showLinkNewDevice by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.QrCodeLogin)
        }.collectAsState(initial = false)

        val otherSessions by remember {
            sessionStore.sessionsFlow().map { list ->
                list
                    .filter { it.userId != matrixClient.sessionId.value }
                    .map {
                        MatrixUser(
                            userId = UserId(it.userId),
                            displayName = it.userDisplayName,
                            avatarUrl = it.userAvatarUrl,
                        )
                    }
                    .toImmutableList()
            }
        }.collectAsState(initial = persistentListOf())

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        val hasAnalyticsProviders = remember { analyticsService.getAvailableAnalyticsProviders().isNotEmpty() }

        // We should display the 'complete verification' option if the current session can be verified

        val showSecureBackupIndicator by indicatorService.showSettingChatBackupIndicator()

        val accountManagementUrl: MutableState<String?> = remember {
            mutableStateOf(null)
        }
        var canDeactivateAccount by remember {
            mutableStateOf(false)
        }
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        LaunchedEffect(Unit) {
            canDeactivateAccount = matrixClient.canDeactivateAccount()
        }

        val nbOfBlockedUsers by produceState(initialValue = 0) {
            matrixClient.ignoredUsersFlow
                .onEach { value = it.size }
                .launchIn(this)
        }

        val showLabsItem = remember { featureFlagService.getAvailableFeatures(isInLabs = true).isNotEmpty() }
        val isLockScreenPinSetup by remember {
            lockScreenService.isPinSetup()
        }.collectAsState(initial = true)

        // GUA FORK: the nudge banner advertises the account (2SV) PIN, so gate it on the account PIN
        // status from the identity service (mirrors TwoStepVerificationPresenter), not the local app-lock.
        val isAccountPinSetup by produceState(initialValue = false) {
            val accessToken = sessionStore.getSession(matrixClient.sessionId.value)?.accessToken ?: return@produceState
            identityServiceClient.pinStatus(accessToken, matrixClient.sessionId.value)
                .onSuccess { value = it.hasPin }
        }

        val directLogoutState = directLogoutPresenter.present()

        LaunchedEffect(Unit) {
            initAccountManagementUrl(accountManagementUrl)
        }

        val showDeveloperSettings by showDeveloperSettingsProvider.showDeveloperSettings.collectAsState()

        fun handleEvent(event: PreferencesRootEvent) {
            when (event) {
                is PreferencesRootEvent.OnVersionInfoClick -> {
                    showDeveloperSettingsProvider.unlockDeveloperSettings(coroutineScope)
                }
                is PreferencesRootEvent.SwitchToSession -> coroutineScope.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
            }
        }

        return PreferencesRootState(
            myUser = matrixUser.value,
            version = remember { versionFormatter.get() },
            deviceId = matrixClient.deviceId,
            isMultiAccountEnabled = isMultiAccountEnabled,
            otherSessions = otherSessions,
            // GUA FORK: hidden from users, developer-only.
            //
            // This screen is upstream's recovery-key console: a key-storage toggle, "set up
            // recovery", "change recovery key", "confirm recovery key". Every one of those is a
            // thing Gua promises never to put in front of anyone. An earlier version of this fork
            // forced it visible on the grounds that it was the only way back from broken key
            // storage; that is no longer true, because the setup banner now repairs the account
            // silently and escalates to a reset on its own when it has to.
            showSecureBackup = showDeveloperSettings,
            showSecureBackupBadge = showSecureBackupIndicator,
            accountManagementUrl = accountManagementUrl.value,
            showAnalyticsSettings = hasAnalyticsProviders,
            canReportBug = canReportBug,
            showLinkNewDevice = showLinkNewDevice,
            showDeveloperSettings = showDeveloperSettings,
            canDeactivateAccount = canDeactivateAccount,
            nbOfBlockedUsers = nbOfBlockedUsers,
            showLabsItem = showLabsItem,
            isLockScreenPinSetup = isLockScreenPinSetup,
            isAccountPinSetup = isAccountPinSetup,
            directLogoutState = directLogoutState,
            snackbarMessage = snackbarMessage,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.initAccountManagementUrl(
        accountManagementUrl: MutableState<String?>,
    ) = launch {
        accountManagementUrl.value = matrixClient.getAccountManagementUrl(null)
            .getOrNull()
            ?.let {
                sessionEnterpriseService.tweakMasUrl(it)
            }
    }
}
