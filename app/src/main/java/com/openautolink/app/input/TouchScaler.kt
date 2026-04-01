package com.openautolink.app.input

/**
 * Scales touch coordinates from SurfaceView pixel space to video resolution space.
 *
 * The SurfaceView may be a different size than the video stream (e.g., surface is 2628x800
 * but video is 1920x1080). Touch coordinates must be mapped proportionally so taps land
 * on the correct UI element in the Android Auto projection.
 */
object TouchScaler {

    /**
     * Scale a coordinate from surface space to video space.
     *
     * @param surfaceCoord The coordinate in SurfaceView pixels (x or y)
     * @param surfaceDimension The SurfaceView dimension (width or height) corresponding to the axis
     * @param videoDimension The video stream dimension (width or height) corresponding to the axis
     * @return The coordinate in video pixels, clamped to [0, videoDimension]
     */
    fun scale(surfaceCoord: Float, surfaceDimension: Int, videoDimension: Int): Float {
        if (surfaceDimension <= 0 || videoDimension <= 0) return 0f
        val scaled = surfaceCoord * videoDimension / surfaceDimension
        return scaled.coerceIn(0f, videoDimension.toFloat())
    }

    /**
     * Scale x/y coordinates from surface space to video space.
     *
     * @return Pair of (scaledX, scaledY) in video pixel coordinates
     */
    fun scalePoint(
        surfaceX: Float,
        surfaceY: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Pair<Float, Float> {
        return Pair(
            scale(surfaceX, surfaceWidth, videoWidth),
            scale(surfaceY, surfaceHeight, videoHeight)
        )
    }
}
