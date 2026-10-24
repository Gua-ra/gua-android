/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.error

import androidx.annotation.StringRes
import io.element.android.features.login.impl.changeserver.AccountProviderAccessException
import io.element.android.libraries.guaresolver.ResolverError
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.ui.strings.CommonStrings

sealed class ChangeServerError : Exception() {
    // GUA FORK: a string resource, never an exception message, so the dialog follows the app language.
    data class Error(
        @StringRes val messageId: Int = CommonStrings.error_unknown,
    ) : ChangeServerError()

    data class NeedElementPro(
        val unauthorisedAccountProviderTitle: String,
        val applicationId: String,
    ) : ChangeServerError()

    data class UnauthorizedAccountProvider(
        val unauthorisedAccountProviderTitle: String,
        val authorisedAccountProviderTitles: List<String>,
    ) : ChangeServerError()

    // GUA FORK: the resolver asked the app to try again later; [wait] is null when it did not say how long.
    sealed class RetryLater : ChangeServerError() {
        abstract val wait: RetryWait?
    }

    data class RateLimited(override val wait: RetryWait?) : RetryLater()
    data class TemporarilyUnavailable(override val wait: RetryWait?) : RetryLater()

    data object SlidingSyncAlert : ChangeServerError()
    data object InvalidServer : ChangeServerError()
    data object UnsupportedServer : ChangeServerError()

    companion object {
        fun from(error: Throwable): ChangeServerError = when (error) {
            is ChangeServerError -> error
            is AuthenticationException -> {
                when (error) {
                    is AuthenticationException.SlidingSyncVersion -> SlidingSyncAlert
                    is AuthenticationException.InvalidServerName,
                    is AuthenticationException.ServerUnreachable -> InvalidServer
                    // AccountAlreadyLoggedIn error should not happen at this point
                    is AuthenticationException.AccountAlreadyLoggedIn,
                    is AuthenticationException.Generic,
                    is AuthenticationException.OAuth -> Error()
                }
            }
            is ResolverError.ResolveRateLimited -> RateLimited(RetryWait.fromRetryAfter(error.retryAfterSeconds))
            is ResolverError.TemporarilyUnavailable -> TemporarilyUnavailable(RetryWait.fromRetryAfter(error.retryAfterSeconds))
            is ResolverError.Transport,
            is ResolverError.Server -> Error(messageId = CommonStrings.error_network_or_server_issue)
            is AccountProviderAccessException.NeedElementProException -> NeedElementPro(
                unauthorisedAccountProviderTitle = error.unauthorisedAccountProviderTitle,
                applicationId = error.applicationId,
            )
            is AccountProviderAccessException.UnauthorizedAccountProviderException -> UnauthorizedAccountProvider(
                unauthorisedAccountProviderTitle = error.unauthorisedAccountProviderTitle,
                authorisedAccountProviderTitles = error.authorisedAccountProviderTitles,
            )
            else -> Error()
        }
    }
}
