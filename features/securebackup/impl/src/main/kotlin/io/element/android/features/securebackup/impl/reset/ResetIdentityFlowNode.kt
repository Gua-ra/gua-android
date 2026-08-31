/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.securebackup.impl.reset.password.ResetIdentityPasswordNode
import io.element.android.features.securebackup.impl.reset.root.ResetIdentityRootNode
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.encryption.EncryptionRepairOutcome
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.IdentityOAuthResetHandle
import io.element.android.libraries.matrix.api.encryption.IdentityPasswordResetHandle
import io.element.android.libraries.matrix.api.encryption.provisionAfterReset
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@ContributesNode(SessionScope::class)
@AssistedInject
class ResetIdentityFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val resetIdentityFlowManager: ResetIdentityFlowManager,
    private val encryptionService: EncryptionService,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val sessionVerificationService: SessionVerificationService,
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
    }

    private lateinit var activity: Activity
    private var darkTheme: Boolean = false
    private var resetJob: Job? = null
    private var hasFinished = false

    /** True from the moment MAS approval starts committing until the reset has settled. */
    private var resetInFlight = false

    override fun onBuilt() {
        super.onBuilt()

        resetIdentityFlowManager.whenResetIsDone {
            finishOnce()
        }
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            is NavTarget.Root -> {
                val callback = object : ResetIdentityRootNode.Callback {
                    override fun onContinue() {
                        sessionCoroutineScope.startReset()
                    }
                }
                createNode<ResetIdentityRootNode>(buildContext, listOf(callback))
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
            return@launch
        }
        // Instead of cancelling the reset job on every ON_START, we can do it before starting a new attempt
        cancelResetJob()

        val handleResult = resetIdentityFlowManager.getResetHandle()
            // We're only interested in the success/failure case, and we need this flow to stop by itself
            // since each call to `startReset` will create a new one
            .first { it.isSuccess() || it.isFailure() }

        when (handleResult) {
            is AsyncData.Failure -> {
                cancelResetJob()
                Timber.e(handleResult.error, "Could not load the reset identity handle.")
            }
            is AsyncData.Success -> {
                when (val handle = handleResult.data) {
                    null -> {
                        Timber.d("No reset handle return, the reset is done.")
                    }
                    is IdentityOAuthResetHandle -> {
                        Timber.d("Launching reset confirmation in MAS")
                        val url = sessionEnterpriseService.tweakMasUrl(handle.url)
                        activity.openUrlInChromeCustomTab(null, darkTheme, url)
                        Timber.d("Starting resetOAuth")
                        resetJob = launch {
                            resetInFlight = true
                            var succeeded = false
                            handle.resetOAuth()
                                .onFailure { Timber.e(it, "The identity reset failed.") }
                                .onSuccess {
                                    succeeded = true
                                    // Front the app BEFORE provisioning. Sync stops a few seconds
                                    // after backgrounding and the recovery/backup flows pin to
                                    // WAITING_FOR_SYNC while it is stopped, so provisioning behind
                                    // the Custom Tab fires into a client that cannot observe it.
                                    returnFromCustomTab()
                                    provisionKeyStorageSilently()
                                }
                            resetInFlight = false
                            returnFromCustomTab()
                            // Only leave on success. A failed reset used to pop exactly like a
                            // successful one, returning the user to the room list with the backup
                            // already destroyed, cross-signing still broken, the same banner back,
                            // and nothing on screen saying anything had gone wrong. In FTUE it
                            // also let them out of onboarding unverified.
                            if (succeeded) finishOnce()
                        }
                        resetJob?.invokeOnCompletion { Timber.d("resetOAuth ended") }
                    }
                    is IdentityPasswordResetHandle -> backstack.push(NavTarget.ResetPassword)
                }
            }
            else -> Unit
        }
    }

    /**
     * GUA FORK: provisions key storage straight after a reset, and does NOT go through
     * [repairWithoutReset].
     *
     * That path deliberately refuses enableRecovery on an INCOMPLETE account, because enabling
     * rotates the secret store and would invalidate a recovery key saved elsewhere. Immediately
     * after a reset there is no such key left to protect and no cross-signing identity either, so
     * the conservative path can never succeed here: it would return ResetRequired forever and put
     * the setup banner straight back in front of the user who just completed a reset.
     */
    private suspend fun provisionKeyStorageSilently() {
        // The verdict is the recovery state, not enableRecovery's Result: it reports success for a
        // secret store it populated with nothing, which is what put the setup banner back in front
        // of a user who had just finished a reset. See provisionAfterReset.
        when (encryptionService.provisionAfterReset(sessionVerificationService)) {
            EncryptionRepairOutcome.Repaired -> Timber.d("Provisioned key storage after the reset.")
            EncryptionRepairOutcome.NotYet,
            EncryptionRepairOutcome.ResetRequired -> Timber.e("Could not provision key storage after the reset.")
        }
    }

    /**
     * GUA FORK: brings the app back to the front once MAS is done with us.
     *
     * MAS ends its side of the reset with a page telling the user to go back to the app, and the
     * Chrome Custom Tab it is rendered in will happily sit there until someone taps the close
     * button. Reordering our own task to the front dismisses it for them. This only runs after
     * resetOAuth() has returned, so the approval has already happened and nobody is interrupted
     * mid-flow.
     */
    private fun returnFromCustomTab() {
        runCatchingExceptions {
            activity.startActivity(
                Intent(activity, activity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }.onFailure { Timber.w(it, "Could not bring the app back to the front after the reset.") }
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
        if (hasFinished) return
        hasFinished = true
        callback.onDone()
    }

    override fun performUpNavigation(): Boolean {
        val navigatesUp = super.performUpNavigation()

        // This intercepts the back navigation so we only cancel this job when the user actually navigates up
        if (navigatesUp && !resetInFlight) {
            sessionCoroutineScope.launch { resetIdentityFlowManager.cancel() }
            cancelResetJob()
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
        if (startResetState.isLoading()) {
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
}
