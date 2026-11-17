/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.chatbackground

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.libraries.chatbackground.api.ChatArtwork
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ChatArtworkManifestTest {
    // Unit tests run from the module's folder.
    private val assets = File("src/main/assets/${ChatArtwork.ASSETS_FOLDER}")
    private val manifest = Json.parseToJsonElement(File(assets, "manifest.json").readText()).jsonObject
    private val artworks = manifest.getValue("artworks").jsonArray.map { it.jsonObject }

    @Test
    fun `the catalog lists the manifest's artworks in its order`() {
        assertThat(ChatArtwork.entries.map { it.id }).isEqualTo(artworks.map { it.string("id") })
    }

    @Test
    fun `every artwork agrees with the manifest`() {
        ChatArtwork.entries.zip(artworks).forEach { (artwork, entry) ->
            val message = assertWithMessage(artwork.id)
            message.that(artwork.section.id).isEqualTo(entry.string("section"))
            message.that(artwork.isLive).isEqualTo(entry.getValue("live").jsonPrimitive.boolean)
            message.that(artwork.lightFile).isEqualTo(entry.obj("files").string("light"))
            message.that(artwork.darkFile).isEqualTo(entry.obj("files").string("dark"))
            message.that(artwork.bubble.id).isEqualTo(entry.obj("bubble").string("light"))
            message.that(artwork.bubbleDark.id).isEqualTo(entry.obj("bubble").string("dark"))
            message.that(artwork.anchor.x).isEqualTo(entry.obj("anchor").getValue("x").jsonPrimitive.float)
            message.that(artwork.anchor.y).isEqualTo(entry.obj("anchor").getValue("y").jsonPrimitive.float)
            message.that(artwork.fallbackPreset.id).isEqualTo(entry.string("fallback_preset"))
        }
    }

    @Test
    fun `every artwork's images are in the assets with the manifest's checksums`() {
        ChatArtwork.entries.zip(artworks).forEach { (artwork, entry) ->
            val checksums = entry.obj("sha256")
            listOf(artwork.lightFile, artwork.darkFile).forEach { file ->
                assertWithMessage(file).that(File(assets, file).exists()).isTrue()
                assertWithMessage(file).that(sha256(File(assets, file))).isEqualTo(checksums.string(file))
            }
        }
    }

    @Test
    fun `the grain tile is in the assets with the manifest's checksum`() {
        val noise = manifest.obj("noise")
        assertThat(noise.string("file")).isEqualTo(ChatArtwork.NOISE_FILE)
        assertThat(sha256(File(assets, ChatArtwork.NOISE_FILE))).isEqualTo(noise.string("sha256"))
    }
}

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

private fun JsonObject.obj(key: String) = getValue(key).jsonObject

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
