/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications

/** Lets the view tell a failed pusher registration apart from the other settings sharing its action state. */
class EnableNotificationsFailure(cause: Throwable) : Exception(cause)
