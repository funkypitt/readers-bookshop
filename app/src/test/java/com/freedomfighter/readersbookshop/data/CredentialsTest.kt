package com.freedomfighter.readersbookshop.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CredentialsTest {
    private val section = "readers-bookshop"
    private val keys = setOf("annas_key")

    @Test fun buildThenReadGivesTheSameKey() {
        val text = Credentials.build(section, mapOf("annas_key" to "k3y-\"12\"\\345"))
        assertTrue(text.contains("\"format\": \"readers-credentials\""))
        assertEquals(mapOf("annas_key" to "k3y-\"12\"\\345"), Credentials.read(text, section, keys))
    }

    @Test fun anEmptyKeyIsNotExported() {
        val text = Credentials.build(section, mapOf("annas_key" to ""))
        try { Credentials.read(text, section, keys); fail() } catch (e: Credentials.NothingFor) { }
    }

    @Test fun otherSectionsAndUnknownKeysAreIgnored() {
        val file: JsonObject = buildJsonObject {
            put("format", "readers-credentials"); put("version", 1)
            putJsonObject("readers-calendar") { putJsonObject("google") { put("client_id", "id") } }
            putJsonObject("readers-tasks") { put("url", "u"); put("password", "p") }
            putJsonObject(section) { put("annas_key", "abc"); put("mirrors", "later"); put("n", 3) }
        }
        assertEquals(mapOf("annas_key" to "abc"), Credentials.read(file.toString(), section, keys))
    }

    @Test fun aForeignFileIsRefused() {
        for (text in listOf("", "{", "\"text\"", "{\"annas_key\": \"k\"}", "{\"format\": \"readers\", \"readers-bookshop\": {\"annas_key\": \"k\"}}")) {
            try { Credentials.read(text, section, keys); fail("accepted: $text") } catch (e: Credentials.NotCredentials) { }
        }
    }

    @Test fun aFileWithoutThisAppSaysSo() {
        val text = "{\"format\": \"readers-credentials\", \"version\": 1, \"readers-tasks\": {\"url\": \"u\"}}"
        try { Credentials.read(text, section, keys); fail() } catch (e: Credentials.NothingFor) { assertEquals(section, e.section) }
    }
}
