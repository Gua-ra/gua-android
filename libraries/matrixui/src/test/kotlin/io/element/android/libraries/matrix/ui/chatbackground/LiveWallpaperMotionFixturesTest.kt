/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import android.view.Surface
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.math.roundToLong

private const val NANOS_PER_SECOND = 1_000_000_000.0

/** Fixtures from tools/wallpapers/motion-fixtures.mjs, shared with iOS. */
class LiveWallpaperMotionFixturesTest {
    private val fixtures = Json.parseToJsonElement(
        LiveWallpaperMotionFixturesTest::class.java.classLoader!!.getResource("live-wallpaper-motion-fixtures.json")!!.readText()
    ).jsonObject

    @Test
    fun `the tilt follows the gyroscope as the reference does, and a stop keeps it`() {
        fixtures.obj("tilt").array("cases").map { it.jsonObject }.forEach { case ->
            val name = case.string("name")
            val tracker = WallpaperTiltTracker()
            val expected = case.array("expected").iterator()
            case.array("events").map { it.jsonObject }.forEach { event ->
                if (event["stop"]?.jsonPrimitive?.boolean == true) {
                    tracker.stop()
                } else {
                    val time = event.double("t")
                    val (rateX, rateY, rateZ) = event.getValue("rate").doubles()
                    tracker.sample(time, rateX, rateY, rateZ)
                    val (expectedX, expectedY) = expected.next().doubles()
                    assertWithMessage("$name, x at $time").that(tracker.tilt.x).isWithin(1e-9).of(expectedX)
                    assertWithMessage("$name, y at $time").that(tracker.tilt.y).isWithin(1e-9).of(expectedY)
                }
            }
            assertWithMessage(name).that(expected.hasNext()).isFalse()
        }
    }

    @Test
    fun `the device's tilt turns into the interface's frame with the screen`() {
        val rotations = mapOf(0 to Surface.ROTATION_0, 90 to Surface.ROTATION_90, 180 to Surface.ROTATION_180, 270 to Surface.ROTATION_270)
        val cases = fixtures.array("interface").map { it.jsonObject }
        assertThat(cases.map { it.int("rotation") }).containsExactlyElementsIn(rotations.keys)
        cases.forEach { case ->
            val (x, y) = case.getValue("device").doubles()
            val (expectedX, expectedY) = case.getValue("expected").doubles()
            val tilt = wallpaperTiltInInterface(rotations.getValue(case.int("rotation")), WallpaperTiltVector(x, y))
            assertWithMessage("$case").that(tilt.x).isWithin(1e-9).of(expectedX)
            assertWithMessage("$case").that(tilt.y).isWithin(1e-9).of(expectedY)
        }
    }

    @Test
    fun `the still is cropped from a frame larger than the view by the parallax shift, and moves with the tilt`() {
        val still = fixtures.obj("still")
        assertThat(LIVE_STILL_WIDTH.toDouble()).isEqualTo(still.double("width"))
        assertThat(LIVE_STILL_HEIGHT.toDouble()).isEqualTo(still.double("height"))
        still.array("cases").map { it.jsonObject }.forEach { case ->
            val (viewWidth, viewHeight) = case.getValue("view").doubles()
            val (anchorX, anchorY) = case.getValue("anchor").doubles()
            val (tiltX, tiltY) = case.getValue("tilt").doubles()
            val expected = case.obj("expected")
            val shift = wallpaperParallaxShift(viewWidth, viewHeight)
            val rect = wallpaperStillRect(viewWidth, viewHeight, anchorX, anchorY, shift, WallpaperTiltVector(tiltX, tiltY))
            assertWithMessage("$case, shift").that(shift).isWithin(1e-6).of(expected.double("shift"))
            listOf(rect.left, rect.top, rect.width, rect.height).zip(expected.getValue("rect").doubles()).forEach { (actual, expectedValue) ->
                assertWithMessage("$case, rect $rect").that(actual).isWithin(1e-6).of(expectedValue)
            }
        }
    }

    @Test
    fun `a send while a pulse plays continues it from its current value, as the reference does`() {
        fixtures.obj("pulse").array("cases").map { it.jsonObject }.forEach { case ->
            val name = case.string("name")
            val sends = case.getValue("sends").doubles()
            var start: Double? = null
            val recorded = LiveWallpaperSendPulse(seenCount = 0)
            var seen = 0
            case.array("samples").forEach { sample ->
                val (time, expected) = sample.doubles()
                while (seen < sends.size && sends[seen] <= time) {
                    val send = sends[seen]
                    start = send - sendPulseRiseSeconds(start?.let { sendPulse(send - it) } ?: 0.0)
                    seen++
                    recorded.onSendCount(seen, nowNanos = (send * NANOS_PER_SECOND).roundToLong())
                }
                assertWithMessage("$name at $time").that(start?.let { sendPulse(time - it) } ?: 0.0).isWithin(1e-9).of(expected)
                // Looser: the recorded pulse starts on a whole nanosecond and is a float.
                val recordedPulse = recorded.at((time * NANOS_PER_SECOND).roundToLong()).toDouble()
                assertWithMessage("$name at $time, recorded").that(recordedPulse).isWithin(1e-6).of(expected)
            }
            assertWithMessage(name).that(seen).isEqualTo(sends.size)
        }
    }
}

private fun JsonObject.obj(key: String) = getValue(key).jsonObject

private fun JsonObject.array(key: String) = getValue(key).jsonArray

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

private fun JsonObject.double(key: String) = getValue(key).jsonPrimitive.double

private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int

private fun JsonElement.doubles() = jsonArray.map { it.jsonPrimitive.double }
