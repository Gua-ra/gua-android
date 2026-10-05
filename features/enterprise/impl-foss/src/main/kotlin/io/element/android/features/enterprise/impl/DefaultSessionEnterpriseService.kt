/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.isGooglePlayBuild
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultSessionEnterpriseService(
    private val buildMeta: BuildMeta,
) : SessionEnterpriseService {
    override suspend fun init() = Unit
    override suspend fun tweakMasUrl(url: String): String = url

    // GUA FORK: calls are off in the Play build, which declares no call foreground service.
    override suspend fun isElementCallAvailable(): Boolean = !buildMeta.isGooglePlayBuild
}
