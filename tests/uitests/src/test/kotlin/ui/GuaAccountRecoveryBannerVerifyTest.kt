/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.features.home.impl.accountrecovery.AccountRecoveryBannerState
import io.element.android.features.home.impl.accountrecovery.PendingAccountRecovery
import io.element.android.features.home.impl.components.AccountRecoveryBanner
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import org.junit.Rule
import org.junit.Test

/**
 * GUA FORK verification: records the delayed account recovery warning shown on the room list, once
 * with the time it can be finished and once when it can be finished now. The banner has no close
 * button by design.
 *
 * Like [GuaFindFriendsVerifyTest], this records to its own snapshot files and adds no preview, so the
 * shared preview-driven golden set is not re-sharded.
 */
class GuaAccountRecoveryBannerVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaAccountRecoveryBannerFinishableLater() {
        paparazzi.snapshot {
            ElementPreview {
                AccountRecoveryBanner(state = aState(finishableAfter = "April 6, 2027 at 6:35 PM"))
            }
        }
    }

    @Test
    fun guaAccountRecoveryBannerFinishableNow() {
        paparazzi.snapshot {
            ElementPreview {
                AccountRecoveryBanner(state = aState(finishableAfter = null))
            }
        }
    }

    private fun aState(finishableAfter: String?) = AccountRecoveryBannerState(
        pendingRecovery = PendingAccountRecovery(finishableAfter = finishableAfter),
        cancelAction = AsyncAction.Uninitialized,
        eventSink = {},
    )
}
