package com.openautolink.app.diagnostics

import com.openautolink.app.transport.ControlMessage
import com.openautolink.app.transport.ControlMessageSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize app_log message`() {
        val msg = ControlMessage.AppLog(
            ts = 1711929600000,
            level = "INFO",
            tag = "video",
            msg = "Codec selected: c2.qti.avc.decoder (HW)"
        )
        val serialized = ControlMessageSerializer.serialize(msg)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertEquals("app_log", obj["type"]?.jsonPrimitive?.content)
        assertEquals("1711929600000", obj["ts"]?.jsonPrimitive?.content)
        assertEquals("INFO", obj["level"]?.jsonPrimitive?.content)
        assertEquals("video", obj["tag"]?.jsonPrimitive?.content)
        assertEquals("Codec selected: c2.qti.avc.decoder (HW)", obj["msg"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serialize app_telemetry with video and session`() {
        val msg = ControlMessage.AppTelemetry(
            ts = 1711929605000,
            video = VideoTelemetry(
                fps = 58.2f, decoded = 1746, dropped = 3,
                codec = "c2.qti.avc.decoder", width = 1920, height = 1080
            ),
            session = SessionTelemetry(state = "STREAMING", uptimeMs = 30000)
        )
        val serialized = ControlMessageSerializer.serialize(msg)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertEquals("app_telemetry", obj["type"]?.jsonPrimitive?.content)

        val video = obj["video"]?.jsonObject!!
        assertEquals("1920", video["width"]?.jsonPrimitive?.content)
        assertEquals("1080", video["height"]?.jsonPrimitive?.content)
        assertEquals("c2.qti.avc.decoder", video["codec"]?.jsonPrimitive?.content)

        val session = obj["session"]?.jsonObject!!
        assertEquals("STREAMING", session["state"]?.jsonPrimitive?.content)
        assertEquals("30000", session["uptime_ms"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serialize app_telemetry with audio`() {
        val msg = ControlMessage.AppTelemetry(
            ts = 1711929605000,
            audio = AudioTelemetry(
                active = listOf("media"),
                underruns = mapOf("media" to 0L),
                framesWritten = mapOf("media" to 14400L)
            )
        )
        val serialized = ControlMessageSerializer.serialize(msg)
        val obj = json.parseToJsonElement(serialized).jsonObject

        val audio = obj["audio"]?.jsonObject!!
        assertEquals("0", audio["underruns"]?.jsonObject?.get("media")?.jsonPrimitive?.content)
        assertEquals("14400", audio["frames_written"]?.jsonObject?.get("media")?.jsonPrimitive?.content)
    }

    @Test
    fun `serialize app_telemetry with cluster`() {
        val msg = ControlMessage.AppTelemetry(
            ts = 1711929605000,
            cluster = ClusterTelemetry(bound = true, alive = true, rebinds = 0)
        )
        val serialized = ControlMessageSerializer.serialize(msg)
        val obj = json.parseToJsonElement(serialized).jsonObject

        val cluster = obj["cluster"]?.jsonObject!!
        assertEquals("true", cluster["bound"]?.jsonPrimitive?.content)
        assertEquals("true", cluster["alive"]?.jsonPrimitive?.content)
        assertEquals("0", cluster["rebinds"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serialize app_telemetry with null sections`() {
        val msg = ControlMessage.AppTelemetry(ts = 1711929605000)
        val serialized = ControlMessageSerializer.serialize(msg)
        val obj = json.parseToJsonElement(serialized).jsonObject

        assertEquals("app_telemetry", obj["type"]?.jsonPrimitive?.content)
        assertEquals(null, obj["video"])
        assertEquals(null, obj["audio"])
        assertEquals(null, obj["cluster"])
    }
}
