/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

/** Fixed reason codes carried by an invalid or unverifiable check. Never text from the server. */
object VerificationReason {
    const val NO_TRUST_ROOTS = "no_trust_roots"
    const val TRUST_ROOTS_MALFORMED = "trust_roots_malformed"
    const val RESOLVER_NOT_CONFIGURED = "resolver_not_configured"
    const val ROSTER_UNAVAILABLE = "roster_unavailable"
    const val ROSTER_UNVERIFIED = "roster_unverified"
    const val MIXED_ROSTER_FORM = "mixed_roster_form"
    const val PARSE_ERROR = "parse_error"
    const val BAD_SIGNATURE = "bad_signature"
    const val THRESHOLD_NOT_MET = "threshold_not_met"
    const val UNKNOWN_KEY_ID = "unknown_key_id"
    const val NOT_IN_ROSTER = "not_in_roster"
    const val UNSUPPORTED_SCHEMA = "unsupported_schema"
    const val UNSUPPORTED_ALG = "unsupported_alg"
    const val BAD_KEY_ID = "bad_key_id"
    const val BAD_SEQUENCE = "bad_sequence"
    const val BAD_WINDOW = "bad_window"
    const val OUTSIDE_WINDOW = "outside_window"
    const val BAD_SIGNATURE_SHAPE = "bad_signature_shape"
    const val MISSING_SIGNATURE = "missing_signature"
    const val NO_CANONICAL_ENCODING = "no_canonical_encoding"
    const val BAD_SIGNING_KEY = "bad_signing_key"
    const val KEY_CONTINUITY = "key_continuity"
    const val LOG_UNAVAILABLE = "log_unavailable"
    const val LOG_MISMATCH = "log_mismatch"
    const val NOT_LOGGED = "not_logged"
    const val POLICY_UNAVAILABLE = "policy_unavailable"
    const val POLICY_UNVERIFIED = "policy_unverified"
    const val NO_POLICY_KEYS = "no_policy_keys"
    const val TIMEOUT = "timeout"
    const val INTERNAL_ERROR = "internal_error"
}
