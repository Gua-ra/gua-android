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
import androidx.compose.ui.window.DialogProperties
import com.bumble.appyx.core.lifecycle.subscribe
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
import io.element.android.features.securebackup.api.KeyStorageProvisioner
import io.element.android.features.securebackup.impl.reset.password.ResetIdentityPasswordNode
import io.element.android.features.securebackup.impl.reset.root.ResetIdentityRootNode
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.auth.OAuthRedirectUrlProvider
import io.element.android.libraries.matrix.api.encryption.IdentityOAuthResetHandle
import io.element.android.libraries.matrix.api.encryption.IdentityPasswordResetHandle
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
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val keyStorageProvisioner: KeyStorageProvisioner,
    private val oAuthRedirectUrlProvider: OAuthRedirectUrlProvider,
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

    /** Set once MAS is on screen, taken by the attempt that runs when we come back. */
    private var pendingResetHandle: IdentityOAuthResetHandle? = null

    override fun onBuilt() {
        super.onBuilt()

        resetIdentityFlowManager.whenResetIsDone {
            finishOnce()
        }

        // GUA FORK: returning to the foreground is the only signal we get that the user is finished
        // at MAS. Its hand-off brings us forward when they approve, and backing out of the Custom
        // Tab brings us forward when they do not; either way the wait is over.
        lifecycle.subscribe(onResume = { finishResetIfReturningFromMas() })
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
                        // GUA FORK: name our scheme so MAS can hand control back on approval. Its
                        // success page is otherwise a dead end and the Custom Tab sits there until
                        // the user dismisses it themselves.
                        val url = sessionEnterpriseService.tweakMasUrl(handle.url).withReturnScheme()
                        Uri.parse(url).let { parsed ->
                            Timber.d(
                                "MAS approval url: host=${parsed.host} path=${parsed.path} " +
                                    "hasReturnScheme=${parsed.getQueryParameter("gua_return") != null} " +
                                    "params=${parsed.queryParameterNames}"
                            )
                        }
                        activity.openUrlInChromeCustomTab(null, darkTheme, url)

                        // GUA FORK: do not start the reset yet. resetOAuth's approval budget is
                        // about two minutes and it starts when it is called, so calling it here
                        // spends all of it on the time the user takes to read the page; anyone
                        // slower approves into a call that has already given up. Wait until we are
                        // back in the foreground, by which point the approval is in force and one
                        // call settles in about a second.
                        pendingResetHandle = handle
                        Timber.d("Waiting to return to the foreground before resetOAuth")
                    }
                    is IdentityPasswordResetHandle -> backstack.push(NavTarget.ResetPassword)
                }
            }
            else -> Unit
        }
    }

    /**
     * Runs the reset once, on the way back from MAS.
     *
     * Guarded twice: the handle is taken before anything suspends, so two resumes cannot both
     * consume it, and [resetInFlight] stops a slow attempt being joined by another. Each reset
     * deletes the key backup and secret storage again, so a second concurrent one is destructive
     * rather than merely wasteful.
     */
    private fun finishResetIfReturningFromMas() {
        val handle = pendingResetHandle ?: return
        if (resetInFlight) return
        pendingResetHandle = null

        Timber.d("Back in the foreground, starting resetOAuth")
        // On the SESSION scope, not this node's. whenResetIsDone finishes the node the moment the
        // backup transitions to ENABLED, which happens partway through resetOAuth -- and finishing
        // the node cancels its lifecycleScope, which cancelled this job before its callbacks ran:
        // no success, no failure, no provisioning, banner left standing.
        resetJob = sessionCoroutineScope.launch {
            resetInFlight = true
            var succeeded = false
            handle.resetOAuth()
                .onFailure { Timber.e(it, "The identity reset failed.") }
                .onSuccess {
                    succeeded = true
                    // Start it, do not wait for it. The reset has landed; holding the user on the
                    // destructive confirmation screen while key storage provisions is a wait with
                    // nothing to justify it, and it leaves a live "Reset and finish setup" button
                    // in front of someone with nothing else to do. It runs on the session scope,
                    // so finishing this flow does not cancel it, and the setup banner watches it
                    // and says it is working.
                    keyStorageProvisioner.start()
                }
            resetInFlight = false
            // Only leave on success. A failed reset used to pop exactly like a successful one,
            // returning the user to the room list with the backup already destroyed,
            // cross-signing still broken, the same banner back, and nothing on screen saying
            // anything had gone wrong. In FTUE it also let them out of onboarding unverified.
            if (succeeded) finishOnce()
        }
        resetJob?.invokeOnCompletion { Timber.d("resetOAuth ended") }
    }

    /**
     * GUA FORK: names this app's URL scheme on the MAS approval URL.
     *
     * MAS compares it against a fixed allow-list and builds the return URL itself, so this names an
     * app rather than supplying a destination. Its success page uses it to hand control back, which
     * is what closes the Custom Tab instead of leaving the user to dismiss it.
     */
    private fun String.withReturnScheme(): String {
        // The provider hands back "<scheme>:/", the same value the OAuth intent parser matches on.
        val scheme = oAuthRedirectUrlProvider.provide().removeSuffix(":/")
        return Uri.parse(this)
            .buildUpon()
            .appendQueryParameter("gua_return", scheme)
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
