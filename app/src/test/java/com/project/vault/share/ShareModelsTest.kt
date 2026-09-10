package com.project.vault.share

import com.google.gson.Gson
import com.project.vault.api.dto.ShareItemRequest
import com.project.vault.api.dto.ShareResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareModelsTest {

    private val gson = Gson()

    @Test
    fun testShareItemRequestSerialization() {
        val request = ShareItemRequest(
            deviceId = "device-bob-1",
            sharedCredId = 42L,
            content = "encrypted-payload-data"
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"deviceId\":\"device-bob-1\""))
        assertTrue(json.contains("\"sharedCredId\":42"))
        assertTrue(json.contains("\"content\":\"encrypted-payload-data\""))

        val deserialized = gson.fromJson(json, ShareItemRequest::class.java)
        assertEquals("device-bob-1", deserialized.deviceId)
        assertEquals(42L, deserialized.sharedCredId)
        assertEquals("encrypted-payload-data", deserialized.content)
    }

    @Test
    fun testShareItemRequestSerializationWithNullSharedCredId() {
        val request = ShareItemRequest(
            deviceId = "device-bob-1",
            sharedCredId = null,
            content = "encrypted-payload-data"
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"deviceId\":\"device-bob-1\""))
        assertTrue(json.contains("\"content\":\"encrypted-payload-data\""))

        val deserialized = gson.fromJson(json, ShareItemRequest::class.java)
        assertEquals("device-bob-1", deserialized.deviceId)
        assertEquals(null, deserialized.sharedCredId)
    }

    @Test
    fun testShareResponseDeserialization() {
        val json = """{"id": 10, "owner": "alice"}"""
        val response = gson.fromJson(json, ShareResponse::class.java)
        assertEquals(10L, response.id)
        assertEquals("alice", response.owner)
    }

    @Test
    fun testShareItemResponseDeserialization() {
        val json = """[{"deviceId":"device-uuid-bob-1","sharedCredId":10,"content":"encrypted-credential-payload-for-device"}]"""
        val items = gson.fromJson(json, Array<com.project.vault.api.dto.ShareItemResponse>::class.java)
        assertEquals(1, items.size)
        assertEquals("device-uuid-bob-1", items[0].deviceId)
        assertEquals(10L, items[0].sharedCredId)
        assertEquals("encrypted-credential-payload-for-device", items[0].content)
    }
}
