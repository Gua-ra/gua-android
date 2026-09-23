/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.linknewdevice.impl.screens.number

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.linknewdevice.impl.LinkNewMobileHandler
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.matrix.api.linknewdevice.CheckCodeSender
import io.element.android.libraries.matrix.api.linknewdevice.LinkMobileStep
import io.element.android.libraries.matrix.api.logs.LoggerTags
import kotlinx.coroutines.launch
import timber.log.Timber

private val tag = LoggerTag("EnterNumberPresenter", LoggerTags.linkNewDevice)

/**
 * GUA FORK: no navigator.
 *
 * This screen had one destination of its own, the wrong-code error, and it was reachable only from a local
 * check that always said yes. The code is compared inside the ceremony, so a wrong one arrives on the step flow
 * and the flow node maps it to the mismatch screen.
 */
@Inject
class EnterNumberPresenter(
    private val linkNewMobileHandler: LinkNewMobileHandler,
) : Presenter<EnterNumberState> {
    @Composable
    override fun present(): EnterNumberState {
        val coroutineScope = rememberCoroutineScope()
        var number by remember { mutableStateOf("") }
        var sendingCode by remember<MutableState<AsyncAction<Unit>>> { mutableStateOf(AsyncAction.Uninitialized) }

        // GUA FORK: the flow is what reports a wrong code. It arrives as ErrorType.InvalidCheckCode from the
        // ceremony's own confirm step, which is the only place the comparison happens.
        val linkMobileStep by linkNewMobileHandler.stepFlow.collectAsState()

        var checkCodeSender: CheckCodeSender? by remember { mutableStateOf(null) }

        LaunchedEffect(linkMobileStep) {
            when (val step = linkMobileStep) {
                is LinkMobileStep.QrScanned -> {
                    checkCodeSender = step.checkCodeSender
                }
                else -> Unit
            }
        }

        fun handleEvent(event: EnterNumberEvent) {
            when (event) {
                is EnterNumberEvent.UpdateNumber -> {
                    sendingCode = AsyncAction.Uninitialized
                    // Keep only digits as a safety measure
                    number = event.number.filter { it.isDigit() }
                }
                EnterNumberEvent.Continue -> coroutineScope.launch {
                    // Get the current code sender
                    val sender = checkCodeSender
                    if (sender == null) {
                        Timber.tag(tag.value).e("No check code sender available")
                        sendingCode = AsyncAction.Failure(IllegalStateException("No check code sender available"))
                    } else {
                        sendingCode = AsyncAction.Loading
                        // GUA FORK: sent straight into the channel. There is no local pre-check to make first,
                        // and the method that appeared to be one always said yes; a wrong code comes back as a
                        // failed ceremony, which the flow node maps to the mismatch screen.
                        sender.send(number.toUByte())
                            .fold(
                                onSuccess = {
                                    Timber.tag(tag.value).d("Code sent successfully")
                                    // Keep loading, do not set sendingCode to AsyncAction.Success(Unit)
                                },
                                onFailure = {
                                    Timber.tag(tag.value).e(it, "Failed to send number code")
                                    sendingCode = AsyncAction.Failure(it)
                                }
                            )
                    }
                }
            }
        }

        return EnterNumberState(
            number = number,
            sendingCode = sendingCode,
            eventSink = ::handleEvent,
        )
    }
}
