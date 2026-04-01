package com.openautolink.app.video

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * Video decoder interface — consumes OAL video frames, renders to a Surface.
 */
interface VideoDecoder {
    val decoderState: StateFlow<DecoderState>
    val stats: StateFlow<VideoStats>

    /** Bind decoder output to a Surface. Must be called before onFrame. */
    fun attach(surface: Surface, width: Int, height: Int)

    /** Detach from Surface, release codec. */
    fun detach()

    /** Feed a video frame from transport. Called from the session's frame collector. */
    fun onFrame(frame: VideoFrame)

    /** Signal that a keyframe should be requested from the bridge. */
    fun requestKeyframe(): Boolean

    /** Pause decoding (release codec). */
    fun pause()

    /** Resume decoding (recreate codec). */
    fun resume()

    /** Release all resources. */
    fun release()
}
