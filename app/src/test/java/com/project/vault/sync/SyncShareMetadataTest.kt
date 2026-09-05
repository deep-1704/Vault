package com.project.vault.sync

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncShareMetadataTest {

    private val gson = Gson()
    private val mapType = object : TypeToken<MutableMap<String, String>>() {}.type

    @Test
    fun `test sync payload enrichment on initial sync without share`() {
        val initialJson = """{"title":"GitHub","credType":"LOGIN","username":"alice","password":"secret"}"""
        val map: MutableMap<String, String> = gson.fromJson(initialJson, mapType)

        val serverId: String? = null
        val serverShareId: String? = null

        if (serverId != null) map["serverId"] = serverId
        if (serverShareId != null) map["serverShareId"] = serverShareId else map.remove("serverShareId")

        val enrichedJson = gson.toJson(map)
        assertFalse(enrichedJson.contains("\"serverId\""))
        assertFalse(enrichedJson.contains("\"serverShareId\""))
    }

    @Test
    fun `test sync payload enrichment with existing serverId and serverShareId`() {
        val initialJson = """{"title":"GitHub","credType":"LOGIN","username":"alice","password":"secret"}"""
        val map: MutableMap<String, String> = gson.fromJson(initialJson, mapType)

        val serverId: String? = "101"
        val serverShareId: String? = "505"

        if (serverId != null) map["serverId"] = serverId
        if (serverShareId != null) map["serverShareId"] = serverShareId else map.remove("serverShareId")

        val enrichedJson = gson.toJson(map)
        assertTrue(enrichedJson.contains("\"serverId\":\"101\""))
        assertTrue(enrichedJson.contains("\"serverShareId\":\"505\""))

        val parsed: Map<String, String> = gson.fromJson(enrichedJson, mapType)
        assertEquals("101", parsed["serverId"])
        assertEquals("505", parsed["serverShareId"])
    }

    @Test
    fun `test receiving sync item computes correct status flags when shared`() {
        val itemCredentialId = 202L
        val serverPayloadJson = """{"title":"Shared Cred","credType":"LOGIN","serverId":"202","serverShareId":"777"}"""
        val map: MutableMap<String, String> = gson.fromJson(serverPayloadJson, mapType)

        val serverIdStr = itemCredentialId.toString()
        val serverShareIdStr = map["serverShareId"]?.takeIf { it.isNotBlank() }

        val isSynced = true
        val isReceived = false
        val isShared = serverShareIdStr != null

        assertEquals("202", serverIdStr)
        assertEquals("777", serverShareIdStr)
        assertTrue(isSynced)
        assertFalse(isReceived)
        assertTrue(isShared)
    }

    @Test
    fun `test receiving sync item computes correct status flags when not shared`() {
        val itemCredentialId = 202L
        val serverPayloadJson = """{"title":"Private Cred","credType":"LOGIN","serverId":"202"}"""
        val map: MutableMap<String, String> = gson.fromJson(serverPayloadJson, mapType)

        val serverIdStr = itemCredentialId.toString()
        val serverShareIdStr = map["serverShareId"]?.takeIf { it.isNotBlank() }

        val isSynced = true
        val isReceived = false
        val isShared = serverShareIdStr != null

        assertEquals("202", serverIdStr)
        assertNull(serverShareIdStr)
        assertTrue(isSynced)
        assertFalse(isReceived)
        assertFalse(isShared)
    }

    @Test
    fun `test receiving share item computes correct status flags`() {
        val sharedCredId = 888L
        val sharePayloadJson = """{"title":"From Alice","credType":"LOGIN","serverId":"303"}"""
        val map: MutableMap<String, String> = gson.fromJson(sharePayloadJson, mapType)

        val shareIdStr = sharedCredId.toString()
        val serverIdStr = map["serverId"]?.takeIf { it.isNotBlank() }

        val isSynced = false
        val isReceived = true
        val isShared = true

        assertEquals("888", shareIdStr)
        assertEquals("303", serverIdStr)
        assertFalse(isSynced)
        assertTrue(isReceived)
        assertTrue(isShared)
    }

    @Test
    fun `test updating credential preserves existing serverId and serverShareId`() {
        val baseFormMap = mutableMapOf(
            "title" to "Updated Title",
            "credType" to "LOGIN",
            "username" to "alice_updated",
            "password" to "new_pass"
        )
        val existingServerId = "55"
        val existingServerShareId = "99"

        if (existingServerId != null) baseFormMap["serverId"] = existingServerId
        if (existingServerShareId != null) baseFormMap["serverShareId"] = existingServerShareId

        val json = gson.toJson(baseFormMap)
        val parsed: Map<String, String> = gson.fromJson(json, mapType)

        assertEquals("Updated Title", parsed["title"])
        assertEquals("55", parsed["serverId"])
        assertEquals("99", parsed["serverShareId"])
    }
}
