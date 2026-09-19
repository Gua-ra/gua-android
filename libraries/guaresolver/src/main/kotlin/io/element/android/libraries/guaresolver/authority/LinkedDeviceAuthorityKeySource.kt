/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.authority

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * GUA FORK: where a newly linked device's OWN authority key comes from, so the device that linked it can
 * sign a `DeviceGrant` over it (ADM-009 decision 5).
 *
 * THIS IS THE ONE PIECE OF THE GRANT THAT HAS NO TRANSPORT YET, and this type is where that gap is stated
 * rather than hidden. Decision 5 fixes both ends: the new device generates its own key and never receives
 * another device's, and the existing device signs a grant over it. Between those two ends nothing carries
 * the key. The MSC4108 channel is inside the Rust SDK and carries a fixed message set this fork does not
 * extend, and the `/account/authority` wire contract defines no object through which a device publishes a
 * key awaiting a grant. Inventing one on this side alone would produce requests the server answers with 404.
 *
 * So [DefaultLinkedDeviceAuthorityKeySource] reports no candidate, the grant offer is not shown, and linking
 * a device behaves exactly as it did before this feature existed. Everything above this seam, the record,
 * the challenge, the signature and the submission, is real and is exercised against a fake candidate.
 */
interface LinkedDeviceAuthorityKeySource {
    /**
     * The key a device that just finished linking published for a grant, or null when there is none.
     *
     * @param accessToken the account's own token: a candidate is only ever read for the account asking.
     */
    suspend fun candidate(accessToken: String): DeviceGrantCandidate?
}

/**
 * A new device's own authority public key, with the label the grant will carry.
 *
 * [label] is what a notification about the grant is allowed to name, so it is the new device's name and not
 * a description of the ceremony.
 */
data class DeviceGrantCandidate(
    val deviceKeyB64Url: String,
    val label: String,
)

/** Reports no candidate, for the reason [LinkedDeviceAuthorityKeySource] states. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLinkedDeviceAuthorityKeySource : LinkedDeviceAuthorityKeySource {
    override suspend fun candidate(accessToken: String): DeviceGrantCandidate? = null
}
