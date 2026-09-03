/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import timber.log.Timber

@Inject
class ResetIdentityRootPresenter(
    private val encryptionService: EncryptionService,
) : Presenter<ResetIdentityRootState> {
    @Composable
    override fun present(): ResetIdentityRootState {
        var displayConfirmDialog by remember { mutableStateOf(false) }

        // GUA FORK: offer recovery from another device only when there is one to recover from:
        // this device is missing keys that exist on the server (recovery is incomplete) AND
        // another device of the account is signed by the current identity. Whether that device
        // still holds the keys and answers is only learnt by trying; the flow says so plainly
        // when it does not. Anything else, and the reset stays the only option.
        val canRecoverFromOtherDevice by produceState(initialValue = false) {
            if (encryptionService.recoveryStateStateFlow.value != RecoveryState.INCOMPLETE) return@produceState
            val hasOtherDevice = encryptionService.hasDevicesToVerifyAgainst().getOrDefault(false)
            Timber.d("Another device signed by the current identity exists: $hasOtherDevice")
            value = hasOtherDevice
        }

        fun handleEvent(event: ResetIdentityRootEvent) {
            displayConfirmDialog = when (event) {
                ResetIdentityRootEvent.Continue -> true
                ResetIdentityRootEvent.DismissDialog -> false
            }
        }

        return ResetIdentityRootState(
            displayConfirmationDialog = displayConfirmDialog,
            canRecoverFromOtherDevice = canRecoverFromOtherDevice,
            eventSink = ::handleEvent,
        )
    }
}
