/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl.oidc

import io.element.android.tests.testutils.lambda.lambdaRecorder

class FakeIdpSessionCleaner(
    private val clearLambda: (String) -> Unit = lambdaRecorder<String, Unit> { },
) : IdpSessionCleaner {
    override suspend fun clear(homeserverUrl: String) {
        clearLambda(homeserverUrl)
    }
}
