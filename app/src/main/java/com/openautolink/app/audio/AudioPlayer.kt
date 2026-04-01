package com.openautolink.app.audio

import com.openautolink.app.transport.AudioPurpose
import kotlinx.coroutines.flow.StateFlow

/**
 * Audio playback interface — manages 5 pre-allocated AudioTrack slots,
 * one per purpose. Routes incoming audio frames by purpose.
 */
interface AudioPlayer {
    val stats: StateFlow<AudioStats>

    /** Pre-allocate all AudioTrack slots. Call at session start. */
    fun initialize()

    /** Release all AudioTrack resources. Call at session end. */
    fun release()

    /** Route an incoming audio frame to the appropriate purpose slot. */
    fun onAudioFrame(frame: AudioFrame)

    /** Activate a purpose slot (start AudioTrack playback). */
    fun startPurpose(purpose: AudioPurpose, sampleRate: Int, channels: Int)

    /** Deactivate a purpose slot (pause AudioTrack, don't release). */
    fun stopPurpose(purpose: AudioPurpose)

    /** Set volume for a specific purpose (0.0 to 1.0). */
    fun setVolume(purpose: AudioPurpose, volume: Float)
}
