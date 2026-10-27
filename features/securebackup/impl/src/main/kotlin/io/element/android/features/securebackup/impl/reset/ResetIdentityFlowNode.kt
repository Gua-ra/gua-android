/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import android.app.Activity
import android.net.Uri
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.securebackup.api.IdentityResetPendingStore
import io.element.android.features.securebackup.api.KeyStorageProvisioner
import io.element.android.features.securebackup.impl.R
import io.element.android.features.securebackup.impl.reset.password.ResetIdentityPasswordNode
import io.element.android.features.securebackup.impl.reset.root.ResetIdentityRootNode
import io.element.android.features.verifysession.api.OutgoingVerificationEntryPoint
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.locale.withUiLocales
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.auth.OAuthRedirectUrlProvider
import io.element.android.libraries.matrix.api.encryption.IdentityOAuthResetHandle
import io.element.android.libraries.matrix.api.encryption.IdentityPasswordResetHandle
import io.element.android.libraries.matrix.api.verification.VerificationRequest
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@ContributesNode(SessionScope::class)
@AssistedInject
class ResetIdentityFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val resetIdentityFlowManager: ResetIdentityFlowManager,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val keyStorageProvisioner: KeyStorageProvisioner,
    private val oAuthRedirectUrlProvider: OAuthRedirectUrlProvider,
    private val matrixClient: MatrixClient,
    private val oAuthActionFlow: OAuthActionFlow,
    private val identityResetPendingStore: IdentityResetPendingStore,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val dispatchers: CoroutineDispatchers,
    private val sessionStore: SessionStore,
    private val okHttpClient: () -> OkHttpClient,
    private val outgoingVerificationEntryPoint: OutgoingVerificationEntryPoint,
) : BaseFlowNode<ResetIdentityFlowNode.NavTarget>(
    backstack = BackStack(initialElement = NavTarget.Root, savedStateMap = buildContext.savedStateMap),
    buildContext = buildContext,
    plugins = plugins,
) {
    interface Callback : Plugin {
        fun onDone()
    }

    private val callback: Callback = callback()

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data object ResetPassword : NavTarget

        /** GUA FORK: verify with another device of the account so it hands the keys over. */
        @Parcelize
        data object RecoverFromOtherDevice : NavTarget
    }

    private lateinit var activity: Activity
    private var darkTheme: Boolean = false
    private var resetJob: Job? = null
    private var approvalJob: Job? = null
    private var startResetJob: Job? = null
    private var hasFinished = false

    /** True from the moment the approved upload starts until it has settled one way or the other. */
    private var resetInFlight = false

    private var isDestroyed = false

    /** True while the approved upload runs, so the screen can say so. */
    private val finishing = MutableStateFlow(false)

    /** Set once MAS is on screen, taken by the attempt that runs when the approval comes back. */
    private var pendingResetHandle: IdentityOAuthResetHandle? = null

    private val recoveryFromOtherDevice = RecoveryFromOtherDevice(
        encryptionService = matrixClient.encryptionService,
        snackbarDispatcher = snackbarDispatcher,
    )

    override fun onBuilt() {
        super.onBuilt()

        resetIdentityFlowManager.whenResetIsDone {
            finishOnce()
        }

        // GUA FORK: the approval page hands control back on the app's own scheme once the user
        // has approved, and that arrives here as an OAuth action. It is the only signal worth
        // acting on: a bare return to the foreground also happens when the user backs out of the
        // tab, and uploading then just polls a refusal until the SDK gives up.
        approvalJob = sessionCoroutineScope.launch {
            oAuthActionFlow.collect { action ->
                if (action is OAuthAction.IdentityResetApproved) {
                    oAuthActionFlow.reset()
                    finishApprovedReset()
                }
            }
        }
        lifecycle.subscribe(onDestroy = {
            isDestroyed = true
            approvalJob?.cancel()
            approvalJob = null
            // GUA FORK: an attempt still running on the session scope keeps the guard until it ends.
            val attempt = startResetJob
            if (attempt?.isActive == true) {
                attempt.invokeOnCompletion {
                    sessionCoroutineScope.launch { matrixClient.identityResetGuard.releaseIfIdle() }
                }
            } else {
                sessionCoroutineScope.launch { matrixClient.identityResetGuard.releaseIfIdle() }
            }
        })
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            is NavTarget.Root -> {
                val callback = object : ResetIdentityRootNode.Callback {
                    override fun onContinue() {
                        startResetJob = sessionCoroutineScope.startReset()
                    }

                    override fun onRecoverFromOtherDevice() {
                        // GUA FORK: the verification needs the sync running, which a reset in flight still holds.
                        if (resetInFlight) {
                            snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_reset_still_finishing))
                            return
                        }
                        recoveryFromOtherDevice.recordStateBeforeVerification()
                        pendingResetHandle = null
                        cancelResetJob()
                        sessionCoroutineScope.launch {
                            resetIdentityFlowManager.cancel()
                            matrixClient.identityResetGuard.releaseIfIdle()
                            backstack.push(NavTarget.RecoverFromOtherDevice)
                        }
                    }
                }
                createNode<ResetIdentityRootNode>(buildContext, listOf(callback))
            }
            is NavTarget.RecoverFromOtherDevice -> {
                // GUA FORK: once the two devices agree on the emojis, the SDK asks the other
                // device for the keys this one is missing; nothing is reset and no recovery key
                // is involved. The verdict is taken when the flow ends.
                outgoingVerificationEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = OutgoingVerificationEntryPoint.Params(
                        showDeviceVerifiedScreen = false,
                        verificationRequest = VerificationRequest.Outgoing.CurrentSession,
                        forceVerification = true,
                    ),
                    callback = object : OutgoingVerificationEntryPoint.Callback {
                        override fun onDone() {
                            backstack.pop()
                            finishRecoveryFromOtherDevice()
                        }

                        override fun onBack() {
                            backstack.pop()
                        }

                        override fun navigateToLearnMoreAboutEncryption() = Unit
                    },
                )
            }
            is NavTarget.ResetPassword -> {
                val handle = resetIdentityFlowManager.currentHandleFlow.value.dataOrNull() as? IdentityPasswordResetHandle ?: error("No password handle found")
                createNode<ResetIdentityPasswordNode>(
                    buildContext,
                    listOf(ResetIdentityPasswordNode.Inputs(handle))
                )
            }
        }
    }

    private fun CoroutineScope.startReset() = launch {
        // GUA FORK: never abort a reset that is already committing. A second tap used to cancel
        // the in-flight approval and start again, which is exactly what a confused user does when
        // the MAS tab leaves them looking at this screen.
        if (resetInFlight) {
            Timber.d("A reset is already in flight, ignoring the request.")
            snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_reset_still_finishing))
            return@launch
        }
        // Instead of cancelling the reset job on every ON_START, we can do it before starting a new attempt
        cancelResetJob()

        // GUA FORK: the guard must hold the sync before the SDK mints the new identity.
        if (!matrixClient.identityResetGuard.acquire()) {
            Timber.d("A reset call from an earlier attempt is still running; not starting another.")
            snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_reset_still_finishing))
            return@launch
        }

        val handleResult = resetIdentityFlowManager.getResetHandle()
            // We're only interested in the success/failure case, and we need this flow to stop by itself
            // since each call to `startReset` will create a new one
            .first { it.isSuccess() || it.isFailure() }

        when (handleResult) {
            is AsyncData.Failure -> {
                cancelResetJob()
                Timber.e(handleResult.error, "Could not load the reset identity handle.")
                matrixClient.identityResetGuard.releaseIfIdle()
            }
            is AsyncData.Success -> {
                when (val handle = handleResult.data) {
                    null -> {
                        Timber.d("No reset handle return, the reset is done.")
                        matrixClient.identityResetGuard.releaseIfIdle()
                    }
                    is IdentityOAuthResetHandle -> {
                        // GUA FORK: from here on this account carries a freshly minted identity
                        // that the server has never seen. Until an approval lands it, the setup
                        // banner must not try to repair around it. See IdentityResetPendingStore.
                        identityResetPendingStore.markPending()

                        // GUA FORK: approve from the app's own session first. The Custom Tab
                        // shares Chrome's cookies, which on most phones hold no session at all,
                        // so the page would demand a whole new phone-number login; on a phone
                        // whose browser holds another account it would approve the reset for
                        // that account. The server now accepts the access token the app already
                        // uses, for this user only, and the upload can follow at once.
                        finishing.value = true
                        val approved = approveFromApp(handle.url)
                        if (approved) {
                            pendingResetHandle = handle
                            finishApprovedReset()
                            return@launch
                        }
                        finishing.value = false

                        // Older servers: fall back to the approval page in a Custom Tab. A private
                        // one where the browser supports it, so the page cannot open under a
                        // browser session that belongs to another account.
                        Timber.d("Launching reset confirmation in MAS")
                        val url = sessionEnterpriseService.tweakMasUrl(handle.url).withAppIdentity().withUiLocales()
                        activity.openUrlInChromeCustomTab(null, darkTheme, url, ephemeral = true)

                        // Nothing runs while the tab is open. The approval page hands control
                        // back once the user has approved, and that is the moment to upload;
                        // the handle waits here until then.
                        pendingResetHandle = handle
                        Timber.d("Waiting for the approval to come back before resetOAuth")
                    }
                    is IdentityPasswordResetHandle -> backstack.push(NavTarget.ResetPassword)
                }
            }
            else -> Unit
        }
    }

    /**
     * Asks the server to open the reset window for this account, authenticated with the
     * session's own access token. False when the server does not offer this (an older
     * deployment) or refuses, in which case the approval page is the fallback.
     */
    private suspend fun approveFromApp(approvalUrl: String): Boolean = withContext(dispatchers.io) {
        val accessToken = sessionStore.getSession(matrixClient.sessionId.value)?.accessToken
        if (accessToken.isNullOrEmpty()) return@withContext false
        val endpoint = runCatchingExceptions {
            Uri.parse(approvalUrl).buildUpon().path(APP_APPROVAL_PATH).clearQuery().fragment(null).build().toString()
        }.getOrNull() ?: return@withContext false
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $accessToken")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        runCatchingExceptions { okHttpClient().newCall(request).execute().use { it.code } }
            .onFailure { Timber.w(it, "App-side approval failed; falling back to the approval page.") }
            .map { code ->
                if (code in 200..299) {
                    Timber.d("Reset approved from the app's own session")
                    true
                } else {
                    Timber.w("App-side approval answered $code; falling back to the approval page.")
                    false
                }
            }
            .getOrDefault(false)
    }

    /**
     * Uploads the new identity once the reset is approved. The wait is bounded and the call is never
     * cancelled: a cancelled reset cannot be told apart from a successful one.
     */
    private fun finishApprovedReset() {
        val handle = pendingResetHandle ?: return
        if (resetInFlight) return
        pendingResetHandle = null

        Timber.d("Approval came back, starting resetOAuth")
        resetInFlight = true
        finishing.value = true

        val operation = matrixClient.identityResetGuard.runReset {
            withContext(dispatchers.io) { handle.resetOAuth() }
        }

        // On the SESSION scope, not this node's. whenResetIsDone finishes the node the moment the
        // backup transitions to ENABLED, which happens partway through resetOAuth, and finishing
        // the node cancels its lifecycleScope.
        resetJob = sessionCoroutineScope.launch {
            val watched = withTimeoutOrNull(RESET_CALL_CEILING) { operation.await() }
            if (watched == null) {
                Timber.w("resetOAuth has not settled within $RESET_CALL_CEILING; it keeps running under the guard.")
                finishing.value = false
                snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_reset_still_finishing))
            }

            val result = watched ?: operation.await()
            finishing.value = false

            try {
                result.fold(
                    onSuccess = {
                        identityResetPendingStore.clear()
                        keyStorageProvisioner.start()
                        finishOnce()
                    },
                    onFailure = { error ->
                        Timber.e(error, "The identity reset failed after the approval came back.")
                        abandonAttempt()
                    },
                )
            } finally {
                // Cleared after the outcome is handled, so a tap cannot cancel the failure clean-up.
                resetInFlight = false
            }
        }
        resetJob?.invokeOnCompletion { Timber.d("resetOAuth ended") }
    }

    /**
     * Stops trusting the current handle and returns the screen to a retryable state.
     *
     * The manager's cancel is only ever called here, after the attempt's result has been
     * discarded, so the SDK's habit of turning a cancelled call into a success can no longer
     * mislead anyone. The next tap prepares a fresh reset, which is safe: the server side is
     * idempotent and the approval window is long. The pending marker stays until one lands.
     */
    private suspend fun abandonAttempt() {
        resetIdentityFlowManager.cancel()
        snackbarDispatcher.post(SnackbarMessage(R.string.gua_encryption_reset_failed))
    }

    private fun finishRecoveryFromOtherDevice() {
        sessionCoroutineScope.launch {
            finishing.value = true
            val outcome = recoveryFromOtherDevice.awaitOutcome(RECOVERY_FROM_OTHER_DEVICE_CEILING)
            finishing.value = false
            recoveryFromOtherDevice.report(outcome, closeFlow = ::finishOnce)
        }
    }

    /**
     * GUA FORK: names this app's URL scheme and account on the MAS approval URL.
     *
     * The scheme is what lets the approval page hand control back, which is what closes the tab
     * instead of leaving the user to dismiss it. The account is what lets the page refuse a
     * browser session for someone else: the tab shares cookies with the system browser, so the
     * page can otherwise open under another account's session and approve the reset for that
     * account while ours keeps being refused. MAS compares and, on a mismatch, signs that session
     * out and asks for a login as this account first. Only ever used to refuse, never to grant.
     */
    private fun String.withAppIdentity(): String {
        // The provider hands back "<scheme>:/", the same value the OAuth intent parser matches on.
        val scheme = oAuthRedirectUrlProvider.provide().removeSuffix(":/")
        val userId = matrixClient.sessionId.value
        val localpart = userId.removePrefix("@").substringBefore(':')
        return Uri.parse(this)
            .buildUpon()
            .appendQueryParameter("gua_return", scheme)
            .appendQueryParameter("gua_user", localpart)
            .appendQueryParameter("org.matrix.msc4198.login_hint", "mxid:$userId")
            .build()
            .toString()
    }

    /**
     * GUA FORK: leaves this flow as soon as the reset is over, whatever happened next.
     *
     * [ResetIdentityFlowManager.whenResetIsDone] only calls back once the backup reaches
     * ENABLED, so if provisioning above fails the user is stranded on the screen holding the
     * button that sent them to MAS, and pressing it again walks them straight back into MAS. The
     * reset itself has already succeeded by this point, so leave regardless; a device whose key
     * storage is still unprovisioned gets the setup banner on the room list and repairs from there.
     */
    private fun finishOnce() {
        if (hasFinished || isDestroyed) return
        hasFinished = true
        callback.onDone()
    }

    override fun performUpNavigation(): Boolean {
        val navigatesUp = super.performUpNavigation()

        // This intercepts the back navigation so we only cancel this job when the user actually navigates up
        if (navigatesUp && !resetInFlight) {
            // Cleared synchronously so a late approval cannot upload with a handle about to be cancelled.
            pendingResetHandle = null
            cancelResetJob()
            sessionCoroutineScope.launch {
                if (resetInFlight) return@launch
                resetIdentityFlowManager.cancel()
                matrixClient.identityResetGuard.releaseIfIdle()
            }
        }

        return navigatesUp
    }

    private fun cancelResetJob() {
        resetJob?.cancel()
        resetJob = null
    }

    @Composable
    override fun View(modifier: Modifier) {
        // Workaround to get the current activity
        if (!this::activity.isInitialized) {
            activity = requireNotNull(LocalActivity.current)
        }
        darkTheme = !ElementTheme.isLightTheme
        val startResetState by resetIdentityFlowManager.currentHandleFlow.collectAsState()
        val isFinishing by finishing.collectAsState()
        if (isFinishing) {
            // GUA FORK: the wait between the approval coming back and the setup being confirmed
            // is short, but it must be visible, and it must not be interruptible.
            ProgressDialog(
                text = stringResource(R.string.gua_encryption_reset_finishing),
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            )
        } else if (startResetState.isLoading()) {
            // GUA FORK: honest about not being cancellable. Both dismiss flags were true, but the
            // handler called cancelResetJob() while resetJob was still null (it is assigned only
            // once the handle arrives), so back was swallowed and nothing was cancelled. Nor should
            // it be: reset_identity() deletes the key backup before it returns, so abandoning it
            // mid-call leaves the account worse off with no way to tell.
            ProgressDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            )
        }

        BackstackView(modifier)
    }

    private companion object {
        /**
         * How long the upload may take once the approval page has handed control back. Generous
         * for the happy path, which settles in a second or two, and short enough that a refused
         * upload cannot turn into minutes of spinner.
         */
        val RESET_CALL_CEILING = 20.seconds

        /** The server endpoint that opens the reset window for the caller's own account. */
        const val APP_APPROVAL_PATH = "/api/gua/identity-reset/allow"

        /**
         * The other device answers within a second or two once the emojis match, but the keys
         * ride on the encryption sync, which polls every 30 s at rest. The bound covers one poll
         * with margin; it only exists so that a device that never answers cannot hold the user
         * on a spinner. If the keys arrive after it, the banner still clears by itself.
         */
        val RECOVERY_FROM_OTHER_DEVICE_CEILING = 60.seconds
    }
}
