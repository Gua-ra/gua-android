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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
            message.that(artwork.shaderFile).isEqualTo(entry.obj("files")["agsl"]?.jsonPrimitive?.content)
            if (artwork.isLive) {
                message.that(artwork.stillTime).isEqualTo(entry.getValue("still_time").jsonPrimitive.float)
            } else {
                message.that(entry["still_time"]).isNull()
            }
        }
    }

    @Test
    fun `every photo has the manifest's credit and no other artwork has one`() {
        ChatArtwork.entries.zip(artworks).forEach { (artwork, entry) ->
            val message = assertWithMessage(artwork.id)
            val credit = artwork.credit
            if (credit == null) {
                message.that(entry.containsKey("credit")).isFalse()
            } else {
                val entryCredit = entry.obj("credit")
                message.that(credit.author).isEqualTo(entryCredit.string("author"))
                message.that(credit.licence.manifestName).isEqualTo(entryCredit.string("licence"))
                message.that(credit.licence.deedUrl).isEqualTo(entryCredit.string("licence_url"))
                message.that(credit.sourceUrl).isEqualTo(entryCredit.getValue("source_url").jsonPrimitive.contentOrNull)
                message.that(entryCredit.string("modified")).isEqualTo("cropped and resized")
            }
        }
    }

    @Test
    fun `every photo carries its credit in XMP and no EXIF`() {
        ChatArtwork.photos.forEach { photo ->
            val credit = requireNotNull(photo.credit)
            val chunks = webpChunks(File(assets, photo.lightFile))
            val message = assertWithMessage(photo.id)
            message.that(chunks.keys).doesNotContain("EXIF")
            val xmp = chunks.getValue("XMP ").decodeToString()
            message.that(xmp).contains("<dc:creator><rdf:Seq><rdf:li>${xmlEscaped(credit.author)}</rdf:li></rdf:Seq></dc:creator>")
            message.that(xmp).contains("<xmpRights:WebStatement>${credit.licence.deedUrl}</xmpRights:WebStatement>")
            val source = credit.sourceUrl
            if (source == null) {
                message.that(xmp).doesNotContain("<dc:source>")
            } else {
                message.that(xmp).contains("<dc:source>${xmlEscaped(source)}</dc:source>")
            }
        }
    }

    @Test
    fun `every artwork's images and Android shader are in the assets with the manifest's checksums`() {
        ChatArtwork.entries.zip(artworks).forEach { (artwork, entry) ->
            val checksums = entry.obj("sha256")
            listOfNotNull(artwork.lightFile, artwork.darkFile, artwork.shaderFile).forEach { file ->
                assertWithMessage(file).that(File(assets, file).exists()).isTrue()
                assertWithMessage(file).that(sha256(File(assets, file))).isEqualTo(checksums.string(file))
            }
        }
    }

    @Test
    fun `the assets hold no iOS shaders`() {
        assertThat(assets.list().orEmpty().filter { it.endsWith(".msl") }).isEmpty()
    }

    @Test
    fun `every Android shader declares the uniforms the renderer sets`() {
        val uniform = Regex("""^uniform float(\d?) (\w+);$""", RegexOption.MULTILINE)
        ChatArtwork.entries.mapNotNull { it.shaderFile }.forEach { file ->
            val declared = uniform.findAll(File(assets, file).readText()).associate { match ->
                match.groupValues[2] to (match.groupValues[1].toIntOrNull() ?: 1)
            }
            assertWithMessage(file).that(declared).isEqualTo(LIVE_WALLPAPER_UNIFORMS)
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

private fun webpChunks(file: File): Map<String, ByteArray> {
    val bytes = file.readBytes()
    check(bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WEBP") { "${file.name} is not a WebP file" }
    val chunks = mutableMapOf<String, ByteArray>()
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        chunks[bytes.decodeToString(offset, offset + 4)] = bytes.copyOfRange(offset + 8, offset + 8 + size)
        offset += 8 + size + size % 2
    }
    return chunks
}

private fun xmlEscaped(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
