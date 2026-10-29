/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.guaresolver.verification

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The policy and placement checks against a scripted resolver serving the published vectors. */
class DefaultResolverVerifierPolicyTest {
    @Test
    fun `a served policy is verified under the policy keys and against the log`() = runTest {
        val resolver = policyRoutingToBr()
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Match)
        assertThat(report.overall).isEqualTo(OverallOutcome.Verified)
        assertThat(resolver.requests).containsExactly("/roster", "/policy/routing", "/roster/log").inOrder()
        server.shutdown()
    }

    @Test
    fun `a 404 while the signed log publishes a policy is policy_not_served`() = runTest {
        // r2's checkpoint covers the POLICY_PUBLISH leaf of br-policy v3.
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.POLICY_NOT_SERVED))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a 404 without a served log is log_unavailable`() = runTest {
        val resolver = ScriptedResolver().apply { roster = { ok(servedRoster("r1").toString()) } }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a policy whose later version is logged within the checkpoint is policy_superseded`() = runTest {
        // The 8th leaf publishes br-policy v4; a checkpoint of 8 signs it.
        val resolver = ScriptedResolver().apply {
            roster = { ok(resigned(withCheckpoint(servedRoster("r2"), size = 8)).toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.POLICY_SUPERSEDED))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a policy signed by the operational key is invalid`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(policyRejection(OPERATIONAL_KEY_REJECTION).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.UNKNOWN_KEY_ID))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("policy"))
        server.shutdown()
    }

    @Test
    fun `a served policy without policy keys is unverifiable, never trusted through the authority keys`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server, trustRoots = authorityRoots()), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.NO_POLICY_KEYS))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("policy"))
        server.shutdown()
    }

    @Test
    fun `a policy whose log is not served is unverifiable`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.LOG_UNAVAILABLE))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a log that does not commit to the roster checkpoint makes the policy invalid`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson(logEvents().drop(1))) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.LOG_MISMATCH))
        server.shutdown()
    }

    @Test
    fun `a policy at its expiresAt is outside_window and the log is not fetched`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server, now = "2030-01-01T00:00:00Z"), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.OUTSIDE_WINDOW))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        assertThat(resolver.requests).doesNotContain("/roster/log")
        server.shutdown()
    }

    @Test
    fun `a verified policy missing from the log is policy_not_logged`() = runTest {
        val unlogged = resignedPolicy(JsonObject(servedPolicy(BR_POLICY) + ("version" to JsonPrimitive(9))))
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(unlogged.toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.POLICY_NOT_LOGGED))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a policy logged only past the checkpoint is policy_newer_than_checkpoint`() = runTest {
        // r1 signs the first 3 leaves; the policy's POLICY_PUBLISH leaf is the 4th.
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r1").toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsBr()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.POLICY_NEWER_THAN_CHECKPOINT))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `a checkpoint past 4096 leaves is log_too_large and the log is not fetched`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(resigned(withCheckpoint(servedRoster("r2"), size = 4097, merkleRoot = rootAt(7))).toString()) }
            policy = { ok(servedPolicy(BR_POLICY).toString()) }
            log = { ok(logJson()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.LOG_TOO_LARGE))
        assertThat(resolver.requests).doesNotContain("/roster/log")
        server.shutdown()
    }

    @Test
    fun `a policy still loading when the budget runs out leaves placement policy_unverified`() = runTest {
        val release = CountDownLatch(1)
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = {
                release.await(BUDGET_SECONDS * 2, TimeUnit.SECONDS)
                MockResponse().setResponseCode(404)
            }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa()))
        release.countDown()

        assertThat(report.logConsistency).isEqualTo(LogConsistencyOutcome.FirstSeen)
        assertThat(report.policy).isEqualTo(PolicyOutcome.Unverifiable(VerificationReason.TIMEOUT))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Unverifiable(VerificationReason.POLICY_UNVERIFIED))
        server.shutdown()
    }

    @Test
    fun `an existing account answer is existing_account even when the placement agrees`() = runTest {
        val server = policyRoutingToBr().start()
        val verifier = createVerifier(server)

        val differing = verify(verifier, answer(hsCa(), exists = true), phone = A_PHONE_PLACED_ON_BR)
        assertThat(differing.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.EXISTING_ACCOUNT))
        assertThat(differing.overall).isEqualTo(OverallOutcome.Unverifiable("placement"))

        val agreeing = verify(verifier, answer(hsBr(), exists = true), phone = A_PHONE_PLACED_ON_BR)
        assertThat(agreeing.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.EXISTING_ACCOUNT))
        server.shutdown()
    }

    @Test
    fun `an existing account answer is existing_account whatever the policy outcome`() = runTest {
        val resolver = ScriptedResolver().apply {
            roster = { ok(servedRoster("r2").toString()) }
            policy = { ok(policyRejection(OPERATIONAL_KEY_REJECTION).toString()) }
        }
        val server = resolver.start()

        val report = verify(createVerifier(server), answer(hsCa(), exists = true))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Invalid(VerificationReason.UNKNOWN_KEY_ID))
        assertThat(report.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.EXISTING_ACCOUNT))
        server.shutdown()
    }

    @Test
    fun `a matching rule in a zone without a delegate signature above the deciding rule is unsigned_rule`() = runTest {
        // r-na in the unsigned na-zone matches the number; the fallback decides hs-ca, which is the answer.
        val server = policyRoutingToBr().start()

        val report = verify(createVerifier(server), answer(hsCa()), phone = A_CANADIAN_PHONE)

        assertThat(report.policy).isEqualTo(PolicyOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.UNSIGNED_RULE))
        server.shutdown()
    }

    @Test
    fun `a placement elsewhere is a mismatch when the answer names the verified roster version`() = runTest {
        val server = policyRoutingToBr().start()

        val report = verify(createVerifier(server), answer(hsCa(), rosterVersion = 7))

        assertThat(report.policy).isEqualTo(PolicyOutcome.Valid)
        assertThat(report.placement).isEqualTo(PlacementOutcome.Mismatch)
        assertThat(report.overall).isEqualTo(OverallOutcome.Failed("placement"))
        server.shutdown()
    }

    @Test
    fun `a placement elsewhere is unattributed when the answer names no roster version`() = runTest {
        val server = policyRoutingToBr().start()

        val report = verify(createVerifier(server), answer(hsCa()))

        assertThat(report.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.UNATTRIBUTED))
        assertThat(report.overall).isEqualTo(OverallOutcome.Unverifiable("placement"))
        server.shutdown()
    }

    @Test
    fun `a placement elsewhere is unattributed when the answer names another roster version`() = runTest {
        val server = policyRoutingToBr().start()

        val report = verify(createVerifier(server), answer(hsCa(), rosterVersion = 6))

        assertThat(report.placement).isEqualTo(PlacementOutcome.Indeterminate(VerificationReason.UNATTRIBUTED))
        server.shutdown()
    }
}
