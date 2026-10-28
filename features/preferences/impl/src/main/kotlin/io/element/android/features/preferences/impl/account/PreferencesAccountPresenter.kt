/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.androidutils.browser.withMxidLoginHint
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient

@Inject
class PreferencesAccountPresenter(
    private val matrixClient: MatrixClient,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val featureFlagService: FeatureFlagService,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val buildMeta: BuildMeta,
) : Presenter<PreferencesAccountState> {
    @Composable
    override fun present(): PreferencesAccountState {
        val matrixUser = matrixClient.userProfile.collectAsState()
        // Note: no need to force a refresh of the profile here, PreferencesRootPresenter, which embeds this presenter, already does it.
        val showLinkNewDevice by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.QrCodeLogin)
        }.collectAsState(initial = false)

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()

        val showSecureBackupIndicator by indicatorService.showSettingChatBackupIndicator()

        var canDeactivateAccount by remember {
            mutableStateOf(false)
        }
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        LaunchedEffect(Unit) {
            canDeactivateAccount = matrixClient.canDeactivateAccount()
        }

        val accountManagementUrl by produceState<String?>(initialValue = null) {
            value = matrixClient.getAccountManagementUrl(null)
                .getOrNull()
                ?.let {
                    sessionEnterpriseService.tweakMasUrl(it)
                }
                // GUA FORK: account management opens in the browser's shared tab, so it can meet a
                // browser session for another account. Naming this one lets the page refuse that
                // session instead of showing someone else's account.
                ?.withMxidLoginHint(matrixClient.sessionId.value)
        }

        return PreferencesAccountState(
            myUser = matrixUser.value,
            // GUA FORK: developer-only. This screen is upstream's recovery-key console (key-storage
            // toggle, set up, change and confirm recovery key), which Gua never puts in front of anyone.
            // The room list banner repairs key storage silently instead.
            showSecureBackup = buildMeta.buildType != BuildType.RELEASE,
            showSecureBackupBadge = showSecureBackupIndicator,
            accountManagementUrl = accountManagementUrl,
            canReportBug = canReportBug,
            showLinkNewDevice = showLinkNewDevice,
            canDeactivateAccount = canDeactivateAccount,
            snackbarMessage = snackbarMessage,
        )
    }
}
