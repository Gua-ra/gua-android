/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl

import android.app.Activity
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.newRoot
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.linknewdevice.api.LinkNewDeviceEntryPoint
import io.element.android.features.linknewdevice.impl.screens.confirmation.CodeConfirmationNode
import io.element.android.features.linknewdevice.impl.screens.desktop.DesktopNoticeNode
import io.element.android.features.linknewdevice.impl.screens.error.ErrorNode
import io.element.android.features.linknewdevice.impl.screens.error.ErrorScreenType
import io.element.android.features.linknewdevice.impl.screens.grantauthority.GrantAuthorityNode
import io.element.android.features.linknewdevice.impl.screens.number.EnterNumberNode
import io.element.android.features.linknewdevice.impl.screens.qrcode.ShowQrCodeNode
import io.element.android.features.linknewdevice.impl.screens.root.LinkNewDeviceRootNode
import io.element.android.features.linknewdevice.impl.screens.scan.ScanQrCodeNode
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.guaresolver.authority.AccountAuthorityManager
import io.element.android.libraries.guaresolver.authority.AuthorityCandidate
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.linknewdevice.ErrorType
import io.element.android.libraries.matrix.api.linknewdevice.LinkDesktopStep
import io.element.android.libraries.matrix.api.linknewdevice.LinkMobileStep
import io.element.android.libraries.matrix.api.logs.LoggerTags
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import timber.log.Timber

private val tag = LoggerTag("LinkNewDeviceFlowNode", LoggerTags.linkNewDevice)

@ContributesNode(SessionScope::class)
@AssistedInject
class LinkNewDeviceFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val linkNewMobileHandler: LinkNewMobileHandler,
    private val linkNewDesktopHandler: LinkNewDesktopHandler,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val sessionId: SessionId,
    // GUA FORK: ADM-009 decision 5. Everything below is inert while the account-authority flag is off,
    // which is every build today: the grant offer is never pushed and this flow ends exactly where it
    // ended before.
    private val featureFlagService: FeatureFlagService,
    private val sessionStore: SessionStore,
    private val authorityManager: AccountAuthorityManager,
) : BaseFlowNode<LinkNewDeviceFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.Root,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    private val callback: LinkNewDeviceEntryPoint.Callback = callback()
    private var activity: Activity? = null
    private var darkTheme: Boolean = false

    /**
     * GUA FORK: whether THIS ceremony got past the secure channel's confirm step.
     *
     * `WaitingForAuth` is emitted only after `confirm()` returned Ok, which is the only place the two-digit
     * check code is compared. It is the load-bearing signal for the grant offer of ADM-009 decision 5, and it
     * is recorded here rather than inferred from a screen, because the SDK exposes no way to ask afterwards
     * and the method that appeared to check the code locally always said yes.
     */
    private var mobileCeremonyConfirmed: Boolean = false

    override fun onBuilt() {
        super.onBuilt()
        var linkMobileHandlerJob: Job? = null
        var linkDesktopHandlerJob: Job? = null

        lifecycle.subscribe(
            onCreate = {
                // GUA FORK: a fresh flow has confirmed nothing yet.
                mobileCeremonyConfirmed = false
                linkNewMobileHandler.reset()
                linkNewDesktopHandler.reset()
                @Suppress("AssignedValueIsNeverRead")
                linkMobileHandlerJob = observeLinkNewMobileHandler()
                @Suppress("AssignedValueIsNeverRead")
                linkDesktopHandlerJob = observeLinkNewDesktopHandler()
            },
            onDestroy = {
                linkMobileHandlerJob?.cancel()
                linkDesktopHandlerJob?.cancel()
            }
        )
    }

    sealed interface NavTarget : Parcelable {
        // Will display the not supported state or the device type selection
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data class MobileShowQrCode(
            val data: String,
        ) : NavTarget

        @Parcelize
        data class CodeConfirmation(
            val code: String,
        ) : NavTarget

        @Parcelize
        data object MobileEnterNumber : NavTarget

        @Parcelize
        data object DesktopNotice : NavTarget

        @Parcelize
        data object DesktopScanQrCode : NavTarget

        @Parcelize
        data class Error(
            val errorScreenType: ErrorScreenType,
        ) : NavTarget

        /**
         * GUA FORK: the offer to give the device that was just linked authority over the account
         * (ADM-009 decision 5). Only reachable from the mobile flow, which is the one direction the
         * record permits: this phone generated the QR and its user typed the check code the new device
         * displayed.
         */
        @Parcelize
        data class GrantAuthority(
            val granteeDeviceKey: String,
            val granteeLabel: String,
            /** The eight characters the user has to see on both phones before anything is signed. */
            val granteeFingerprint: String,
        ) : NavTarget
    }

    private fun observeLinkNewMobileHandler(): Job {
        Timber.tag(tag.value).d("startObservingLinkNewMobileHandler")
        return linkNewMobileHandler.stepFlow
            .onEach { linkMobileStep ->
                Timber.tag(tag.value).d("step: ${linkMobileStep::class.java.simpleName}")
                when (linkMobileStep) {
                    LinkMobileStep.Uninitialized -> Unit
                    LinkMobileStep.Done -> {
                        val candidate = grantCandidate()
                        if (candidate == null) {
                            callback.onDone()
                        } else {
                            backstack.push(
                                NavTarget.GrantAuthority(
                                    granteeDeviceKey = candidate.deviceKeyB64Url,
                                    granteeLabel = candidate.label,
                                    granteeFingerprint = candidate.fingerprint,
                                )
                            )
                        }
                    }
                    is LinkMobileStep.Error -> {
                        navigateToError(linkMobileStep.errorType)
                    }
                    is LinkMobileStep.QrReady -> {
                        // The QrCode is ready, navigate to its display, if not already there
                        val navTarget = backstack.elements.value.last().key.navTarget
                        if (navTarget !is NavTarget.MobileShowQrCode) {
                            backstack.push(NavTarget.MobileShowQrCode(linkMobileStep.data))
                        }
                    }
                    LinkMobileStep.QrRotating -> {
                        // This step is handled in ShowQrCodePresenter
                    }
                    is LinkMobileStep.QrScanned -> {
                        backstack.replace(NavTarget.MobileEnterNumber)
                    }
                    LinkMobileStep.Starting -> {
                        // This step is not received at the moment, so do nothing
                    }
                    LinkMobileStep.SyncingSecrets -> Unit
                    is LinkMobileStep.WaitingForAuth -> {
                        // GUA FORK: the ceremony confirmed, which means the code shown on the new device was
                        // typed here and the channel accepted it.
                        mobileCeremonyConfirmed = true
                        navigateToBrowser(linkMobileStep.verificationUri)
                    }
                }
            }
            .launchIn(sessionCoroutineScope)
    }

    private fun observeLinkNewDesktopHandler(): Job {
        Timber.tag(tag.value).d("startObservingLinkNewDesktopHandler")
        return linkNewDesktopHandler.stepFlow.onEach { linkDesktopStep ->
            Timber.tag(tag.value).d("step: ${linkDesktopStep::class.java.simpleName}")
            when (linkDesktopStep) {
                LinkDesktopStep.Done -> callback.onDone()
                is LinkDesktopStep.Error -> {
                    navigateToError(linkDesktopStep.errorType)
                }
                is LinkDesktopStep.EstablishingSecureChannel -> {
                    backstack.push(NavTarget.CodeConfirmation(linkDesktopStep.checkCodeString))
                }
                is LinkDesktopStep.InvalidQrCode -> {
                    // This error will be handled by the ScanQrCodeNode
                }
                LinkDesktopStep.Starting -> Unit
                LinkDesktopStep.SyncingSecrets -> Unit
                LinkDesktopStep.Uninitialized -> Unit
                is LinkDesktopStep.WaitingForAuth -> {
                    navigateToBrowser(linkDesktopStep.verificationUri)
                }
            }
        }
            .launchIn(sessionCoroutineScope)
    }

    /**
     * GUA FORK: whether there is a grant to offer, which needs four things and refuses on any one of them.
     *
     * The feature has to be on. This phone has to hold authority, because only an active device can sign a
     * grant. THIS ceremony has to have passed the check code, which is what [mobileCeremonyConfirmed] records
     * and what makes the offer reachable from the mobile handler and never from the desktop one: in the other
     * direction the code binds a channel rather than a peer, so the key being signed over would be whatever
     * came up that channel. And the account has to hold a live candidate, which is the new device's own key
     * offered under its own session.
     *
     * A candidate whose fingerprint this client could not recompute is dropped rather than shown: the
     * comparison is the whole binding, and eight characters nobody derived from those 32 bytes bind nothing.
     */
    internal suspend fun grantCandidate(): AuthorityCandidate? {
        if (!featureFlagService.isFeatureEnabled(FeatureFlags.AccountAuthority)) return null
        if (!mobileCeremonyConfirmed) return null
        if (!authorityManager.holdsAuthority()) return null
        val accessToken = sessionStore.getSession(sessionId.value)?.accessToken ?: return null
        return authorityManager.candidates(accessToken).getOrNull()
            .orEmpty()
            .firstOrNull { it.fingerprint.isNotEmpty() }
    }

    private fun navigateToError(errorType: ErrorType) {
        // Map the error to an error screen
        val error = when (errorType) {
            is ErrorType.InvalidCheckCode -> ErrorScreenType.Mismatch2Digits
            is ErrorType.UnsupportedProtocol -> ErrorScreenType.ProtocolNotSupported
            is ErrorType.Cancelled -> ErrorScreenType.Cancelled
            is ErrorType.ConnectionInsecure -> ErrorScreenType.InsecureChannelDetected
            is ErrorType.Expired,
            is ErrorType.NotFound,
            is ErrorType.DeviceNotFound -> ErrorScreenType.Expired
            is ErrorType.OtherDeviceAlreadySignedIn -> ErrorScreenType.OtherDeviceAlreadySignedIn
            // TODO check if we expect to hit this here or if it should be caught earlier on
            is ErrorType.UnsupportedQrCodeType -> ErrorScreenType.UnknownError
            is ErrorType.MissingSecretsBackup,
            is ErrorType.DeviceIdAlreadyInUse,
            is ErrorType.Unknown -> ErrorScreenType.UnknownError
        }
        // It is OK to push on backstack, since when user leaves the error screen, a new root will be set,
        // or the whole flow will be popped.
        backstack.push(NavTarget.Error(error))
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Root -> {
                val callback = object : LinkNewDeviceRootNode.Callback {
                    override fun onDone() {
                        callback.onDone()
                    }

                    override fun linkDesktopDevice() {
                        linkNewDesktopHandler.reset()
                        backstack.push(NavTarget.DesktopNotice)
                    }
                }
                createNode<LinkNewDeviceRootNode>(buildContext, listOf(callback))
            }
            NavTarget.DesktopNotice -> {
                val callback = object : DesktopNoticeNode.Callback {
                    override fun navigateBack() {
                        backstack.pop()
                    }

                    override fun navigateToQrCodeScanner() {
                        backstack.push(NavTarget.DesktopScanQrCode)
                    }
                }
                createNode<DesktopNoticeNode>(buildContext, listOf(callback))
            }
            NavTarget.DesktopScanQrCode -> {
                val callback = object : ScanQrCodeNode.Callback {
                    override fun cancel() {
                        backstack.pop()
                    }
                }
                createNode<ScanQrCodeNode>(buildContext, listOf(callback))
            }
            NavTarget.MobileEnterNumber -> {
                val callback = object : EnterNumberNode.Callback {
                    override fun navigateBack() {
                        backstack.pop()
                    }
                }
                createNode<EnterNumberNode>(buildContext, listOf(callback))
            }
            is NavTarget.CodeConfirmation -> {
                val callback = object : CodeConfirmationNode.Callback {
                    override fun onCancel() {
                        // Push error
                        backstack.push(NavTarget.Error(ErrorScreenType.Cancelled))
                    }
                }
                val inputs = CodeConfirmationNode.Inputs(
                    code = navTarget.code,
                )
                createNode<CodeConfirmationNode>(buildContext, listOf(inputs, callback))
            }
            is NavTarget.MobileShowQrCode -> {
                val callback = object : ShowQrCodeNode.Callback {
                    override fun navigateBack() {
                        linkNewMobileHandler.reset()
                        backstack.pop()
                    }
                }
                val inputs = ShowQrCodeNode.Inputs(
                    data = navTarget.data,
                )
                createNode<ShowQrCodeNode>(buildContext, listOf(inputs, callback))
            }
            is NavTarget.GrantAuthority -> {
                val grantCallback = object : GrantAuthorityNode.Callback {
                    override fun onDone() {
                        callback.onDone()
                    }
                }
                val inputs = GrantAuthorityNode.Inputs(
                    candidate = AuthorityCandidate(
                        deviceKeyB64Url = navTarget.granteeDeviceKey,
                        fingerprint = navTarget.granteeFingerprint,
                        label = navTarget.granteeLabel,
                        expiresAtEpochSeconds = 0,
                    )
                )
                createNode<GrantAuthorityNode>(buildContext, listOf(inputs, grantCallback))
            }
            is NavTarget.Error -> {
                val callback = object : ErrorNode.Callback {
                    override fun onRetry() {
                        // GUA FORK: a retry is a new ceremony, and a new ceremony has confirmed nothing.
                        mobileCeremonyConfirmed = false
                        linkNewMobileHandler.reset()
                        linkNewDesktopHandler.reset()
                        backstack.newRoot(NavTarget.Root)
                    }

                    override fun onCancel() {
                        linkNewMobileHandler.reset()
                        linkNewDesktopHandler.reset()
                        callback.onDone()
                    }
                }
                createNode<ErrorNode>(buildContext, listOf(callback, navTarget.errorScreenType))
            }
        }
    }

    private suspend fun navigateToBrowser(url: String) {
        activity?.openUrlInChromeCustomTab(
            session = null,
            darkTheme = darkTheme,
            // GUA FORK: name this account, so the approval page refuses to approve the new device
            // under a browser session that belongs to someone else.
            url = sessionEnterpriseService.linkNewDeviceBrowserUrl(url, sessionId),
            // GUA FORK: and a private tab, so there is no such session to pick up in the first place.
            ephemeral = true,
        )
    }

    @Composable
    override fun View(modifier: Modifier) {
        activity = requireNotNull(LocalActivity.current)
        darkTheme = !ElementTheme.isLightTheme
        DisposableEffect(Unit) {
            onDispose {
                activity = null
            }
        }
        BackstackView()
    }
}
