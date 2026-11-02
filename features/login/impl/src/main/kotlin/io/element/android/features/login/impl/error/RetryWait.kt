/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.element.android.features.login.impl.R

/**
 * GUA FORK: how long a retry message tells the user to wait. Waits of a minute or more are rounded
 * up to whole minutes so the user is never told to retry early.
 */
sealed interface RetryWait {
    data class Seconds(val count: Int) : RetryWait
    data class Minutes(val count: Int) : RetryWait

    companion object {
        fun fromRetryAfter(seconds: Long?): RetryWait? = when {
            seconds == null || seconds < 1 -> null
            seconds < 60 -> Seconds(seconds.toInt())
            else -> Minutes(((seconds + 59) / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
    }
}

/** GUA FORK: the dialog text for a resolver that asked the app to try again later. */
@Composable
internal fun retryLaterMessage(error: ChangeServerError.RetryLater): String {
    val wait = when (val wait = error.wait) {
        is RetryWait.Seconds -> pluralStringResource(R.plurals.gua_retry_wait_seconds, wait.count, wait.count)
        is RetryWait.Minutes -> pluralStringResource(R.plurals.gua_retry_wait_minutes, wait.count, wait.count)
        null -> null
    }
    return when (error) {
        is ChangeServerError.RateLimited -> if (wait != null) {
            stringResource(R.string.gua_resolver_rate_limited_retry_in, wait)
        } else {
            stringResource(R.string.gua_resolver_rate_limited)
        }
        is ChangeServerError.TemporarilyUnavailable -> if (wait != null) {
            stringResource(R.string.gua_resolver_routing_unavailable_retry_in, wait)
        } else {
            stringResource(R.string.gua_resolver_routing_unavailable)
        }
    }
}
