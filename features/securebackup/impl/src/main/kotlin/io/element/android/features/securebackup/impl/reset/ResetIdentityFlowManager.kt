/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.IdentityResetHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Inject
class ResetIdentityFlowManager(
    private val encryptionService: EncryptionService,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val resetHandleFlow: MutableStateFlow<AsyncData<IdentityResetHandle?>> = MutableStateFlow(AsyncData.Uninitialized)
    val currentHandleFlow: StateFlow<AsyncData<IdentityResetHandle?>> = resetHandleFlow
    private var whenResetIsDoneWaitingJob: Job? = null

    fun whenResetIsDone(block: () -> Unit) {
        whenResetIsDoneWaitingJob = sessionCoroutineScope.launch {
            // GUA FORK: wait for the backup to BECOME enabled, not to already be enabled.
            //
            // backupStateStateFlow is a StateFlow, so `first { it == ENABLED }` also matches the
            // value it is replaying right now. Upstream that is harmless, because you only reach
            // this flow after a reset has destroyed the backup. Gua reaches it from the setup
            // banner, and the account the banner fires on is one whose cross-signing keys are
            // missing while the key backup is perfectly healthy -- so the state was ALREADY
            // ENABLED, this matched on the replayed value, and the reset screen finished itself
            // about a tenth of a second after it opened. What the user sees is Finish setup
            // flashing and doing nothing at all.
            //
            // Dropping that first emission makes this wait for a transition. The reset destroys
            // the backup on its way through, so a genuine completion always produces one.
            encryptionService.backupStateStateFlow.drop(1).first { it == BackupState.ENABLED }
            block()
        }
    }

    fun getResetHandle(): StateFlow<AsyncData<IdentityResetHandle?>> {
        return if (resetHandleFlow.value.isLoading() || resetHandleFlow.value.isSuccess()) {
            resetHandleFlow
        } else {
            resetHandleFlow.value = AsyncData.Loading()

            sessionCoroutineScope.launch {
                encryptionService.startIdentityReset()
                    .onSuccess { handle ->
                        resetHandleFlow.value = AsyncData.Success(handle)
                    }
                    .onFailure {
                        resetHandleFlow.value = AsyncData.Failure(it)
                    }
            }

            resetHandleFlow
        }
    }

    suspend fun cancel() {
        currentHandleFlow.value.dataOrNull()?.cancel()
        resetHandleFlow.value = AsyncData.Uninitialized

        whenResetIsDoneWaitingJob?.cancel()
        whenResetIsDoneWaitingJob = null
    }
}
