package com.openautolink.app.input

import android.view.MotionEvent

/**
 * Forwards Android MotionEvents to the bridge as OAL touch messages.
 * Handles coordinate scaling from surface pixels to video resolution pixels,
 * and converts multi-touch events to the OAL JSON format.
 */
interface TouchForwarder {

    /**
     * Process a MotionEvent from the projection SurfaceView.
     *
     * @param event The Android MotionEvent
     * @param surfaceWidth Width of the SurfaceView in pixels
     * @param surfaceHeight Height of the SurfaceView in pixels
     * @param videoWidth Width of the video stream (from VideoStats)
     * @param videoHeight Height of the video stream (from VideoStats)
     */
    fun onTouch(
        event: MotionEvent,
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int
    )
}
