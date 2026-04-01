package com.openautolink.app.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.openautolink.app.transport.AudioPurpose

/**
 * Manages Android audio focus for the projection session.
 *
 * - MEDIA gets AUDIOFOCUS_GAIN (long-term, exclusive)
 * - NAVIGATION/ALERT get AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK (short, ducks media)
 * - PHONE_CALL gets AUDIOFOCUS_GAIN_TRANSIENT (pauses media)
 * - ASSISTANT gets AUDIOFOCUS_GAIN_TRANSIENT (pauses media)
 *
 * On focus loss: pause but don't release AudioTracks. Resume on focus regain.
 */
class AudioFocusManager(private val audioManager: AudioManager) {

    companion object {
        private const val TAG = "AudioFocusManager"
    }

    private var currentRequest: AudioFocusRequest? = null
    private var onFocusLost: (() -> Unit)? = null
    private var onFocusRegained: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                onFocusRegained?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost: $focusChange")
                onFocusLost?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // AAOS handles ducking via AudioAttributes automatically
                Log.d(TAG, "Audio focus: can duck")
            }
        }
    }

    /**
     * Request audio focus appropriate for the given purpose.
     * Call before writing to AudioTrack.
     */
    fun requestFocus(
        purpose: AudioPurpose,
        onLost: (() -> Unit)? = null,
        onRegained: (() -> Unit)? = null
    ): Boolean {
        this.onFocusLost = onLost
        this.onFocusRegained = onRegained

        releaseFocus()

        val focusGain = when (purpose) {
            AudioPurpose.MEDIA -> AudioManager.AUDIOFOCUS_GAIN
            AudioPurpose.NAVIGATION -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioPurpose.ALERT -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioPurpose.PHONE_CALL -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            AudioPurpose.ASSISTANT -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        }

        val usage = when (purpose) {
            AudioPurpose.MEDIA -> AudioAttributes.USAGE_MEDIA
            AudioPurpose.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            AudioPurpose.ASSISTANT -> AudioAttributes.USAGE_ASSISTANT
            AudioPurpose.PHONE_CALL -> AudioAttributes.USAGE_VOICE_COMMUNICATION
            AudioPurpose.ALERT -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .build()

        val request = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAcceptsDelayedFocusGain(true)
            .build()

        val result = audioManager.requestAudioFocus(request)
        currentRequest = request

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Focus request for $purpose: ${if (granted) "granted" else "denied"}")
        return granted
    }

    /** Release audio focus. Call when all audio purposes stop. */
    fun releaseFocus() {
        currentRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            currentRequest = null
            Log.d(TAG, "Audio focus released")
        }
    }
}
