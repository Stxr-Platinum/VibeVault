package com.vibevault.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

@Serializable
data class TestTrack(val id: String, val name: String)

class SerializationTest {
    @Test
    fun testNullId() {
        val jsonString = """{"id": null, "name": "Test"}"""
        try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val parsed = json.decodeFromString<TestTrack>(jsonString)
            println("Success: ${parsed}")
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
