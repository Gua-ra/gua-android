/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package ui

import androidx.compose.foundation.layout.Column
import app.cash.paparazzi.Paparazzi
import base.BaseDeviceConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserHeader
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import org.junit.Rule
import org.junit.Test

/**
 * Confirms that the homeserver suffix of a Matrix user id is never rendered in user-facing UI. The
 * user has no display name, so the rendered handle comes straight from [UserId.displayHandle]. If the
 * abstraction regressed, the screenshot would show `@ana:dev.local` instead of `@ana`.
 */
class GuaHomeserverAbstractionVerifyTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = BaseDeviceConfig.NEXUS_5.deviceConfig.copy(
            locale = "en",
            softButtons = false,
        ),
        maxPercentDifference = 0.01,
    )

    @Test
    fun guaDisplayHandleHidesHomeserver() {
        val user = MatrixUser(
            userId = UserId("@ana:dev.local"),
            displayName = null,
            avatarUrl = null,
        )
        paparazzi.snapshot {
            ElementTheme {
                Column {
                    MatrixUserHeader(matrixUser = user)
                    MatrixUserRow(matrixUser = user)
                }
            }
        }
    }
}
