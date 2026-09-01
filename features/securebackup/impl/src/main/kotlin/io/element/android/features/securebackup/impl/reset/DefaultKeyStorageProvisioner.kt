/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.securebackup.impl.reset

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.securebackup.api.KeyStorageProvisioner
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.EncryptionRepairOutcome
import io.element.android.libraries.matrix.api.encryption.provisionAfterReset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultKeyStorageProvisioner(
    private val matrixClient: MatrixClient,
) : KeyStorageProvisioner {
    private val provisioning = MutableStateFlow(false)
    private var currentJob: Job? = null

    override val isProvisioning: StateFlow<Boolean> = provisioning.asStateFlow()

    /**
     * Runs on the session scope rather than the caller's, so dismissing the reset flow the instant
     * the reset lands does not cancel the work that flow started.
     */
    override fun start() {
        if (currentJob?.isActive == true) return

        currentJob = matrixClient.sessionCoroutineScope.launch {
            provisioning.value = true
            try {
                when (matrixClient.encryptionService.provisionAfterReset()) {
                    EncryptionRepairOutcome.Repaired ->
                        Timber.d("Provisioned key storage after the reset.")
                    EncryptionRepairOutcome.NotYet,
                    EncryptionRepairOutcome.Failed,
                    EncryptionRepairOutcome.ResetRequired ->
                        // The banner comes back on its own and is the retry. Not a dead end.
                        Timber.e("Could not provision key storage after the reset.")
                }
            } finally {
                provisioning.value = false
            }
        }
    }
}
