package com.openautolink.app.transport.direct

/**
 * AA wire protocol channel IDs.
 * These are the channel numbers in the AA message header (byte 0).
 */
object AaChannel {
    const val CONTROL = 0
    const val SENSOR = 1
    const val VIDEO = 2
    const val INPUT = 3
    const val AUDIO_SPEECH = 4   // AU1 — voice prompts (16kHz mono)
    const val AUDIO_SYSTEM = 5   // AU2 — system sounds (16kHz mono)
    const val AUDIO_MEDIA = 6    // AUD — media playback (48kHz stereo)
    const val MIC = 7
    const val BLUETOOTH = 8
    const val MEDIA_PLAYBACK = 9
    const val NAVIGATION = 10
    const val NOTIFICATION = 11
    const val PHONE_STATUS = 12

    fun isAudio(channel: Int): Boolean =
        channel == AUDIO_MEDIA || channel == AUDIO_SPEECH || channel == AUDIO_SYSTEM

    fun name(channel: Int): String = when (channel) {
        CONTROL -> "CTR"
        SENSOR -> "SEN"
        VIDEO -> "VID"
        INPUT -> "INP"
        AUDIO_SPEECH -> "AU1"
        AUDIO_SYSTEM -> "AU2"
        AUDIO_MEDIA -> "AUD"
        MIC -> "MIC"
        BLUETOOTH -> "BTH"
        MEDIA_PLAYBACK -> "MPB"
        NAVIGATION -> "NAV"
        NOTIFICATION -> "NOT"
        PHONE_STATUS -> "PHN"
        else -> "?$channel"
    }
}

/**
 * AA wire protocol message type constants.
 * Control channel (0) uses ControlMsgType enum values directly.
 * Media channels use MediaMsgType values (0x8000+).
 */
object AaMsgType {
    // Control channel (from control.proto ControlMsgType)
    const val VERSION_REQUEST = 1
    const val VERSION_RESPONSE = 2
    const val SSL_HANDSHAKE = 3
    const val AUTH_COMPLETE = 4
    const val SERVICE_DISCOVERY_REQUEST = 5
    const val SERVICE_DISCOVERY_RESPONSE = 6
    const val CHANNEL_OPEN_REQUEST = 7
    const val CHANNEL_OPEN_RESPONSE = 8
    const val CHANNEL_CLOSE = 9
    const val PING_REQUEST = 11
    const val PING_RESPONSE = 12
    const val NAV_FOCUS_REQUEST = 13
    const val NAV_FOCUS_NOTIFICATION = 14
    const val BYEBYE_REQUEST = 15
    const val BYEBYE_RESPONSE = 16
    const val VOICE_SESSION_NOTIFICATION = 17
    const val AUDIO_FOCUS_REQUEST = 18
    const val AUDIO_FOCUS_NOTIFICATION = 19

    // Media channels (from HUR media.proto MsgType — verified against aasdk)
    const val MEDIA_DATA = 0x0000          // Video/audio frame payload
    const val MEDIA_CODEC_CONFIG = 0x0001  // SPS/PPS codec config
    const val MEDIA_SETUP = 0x8000         // Phone→HU: media channel setup request
    const val MEDIA_START = 0x8001         // Phone→HU: media streaming start
    const val MEDIA_STOP = 0x8002          // Phone→HU: media streaming stop
    const val MEDIA_CONFIG = 0x8003        // HU→Phone: config response (maxUnacked, etc)
    const val MEDIA_ACK = 0x8004           // HU→Phone: frame acknowledgement
    const val MIC_REQUEST = 0x8005         // Phone→HU: start/stop mic capture
    const val MIC_RESPONSE = 0x8006        // HU→Phone: mic response
    const val VIDEO_FOCUS_REQUEST = 0x8007 // Phone→HU: video focus mode change
    const val VIDEO_FOCUS_NOTIFICATION = 0x8008 // HU→Phone: grant/deny video focus
    const val UPDATE_UI_CONFIG_REQUEST = 0x8009 // Phone→HU: UI config update
    const val UPDATE_UI_CONFIG_REPLY = 0x800A   // HU→Phone: UI config reply
    const val AUDIO_UNDERFLOW = 0x800B          // HU→Phone: audio underflow notification

    /** True if the type is a control-range message (1-26 or special values). */
    fun isControl(type: Int): Boolean = type in 1..26
}
