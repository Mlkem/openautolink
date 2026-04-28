package com.openautolink.app.transport.usb

/**
 * AOA v2 (Android Open Accessory) protocol constants.
 *
 * Reference: https://source.android.com/docs/core/interaction/accessories/aoa2
 */
object UsbConstants {
    // AOA control transfer request types
    const val ACC_REQ_GET_PROTOCOL = 51
    const val ACC_REQ_SEND_STRING = 52
    const val ACC_REQ_START = 53

    // String indices for SEND_STRING
    const val ACC_IDX_MANUFACTURER = 0
    const val ACC_IDX_MODEL = 1
    const val ACC_IDX_DESCRIPTION = 2
    const val ACC_IDX_VERSION = 3
    const val ACC_IDX_URI = 4
    const val ACC_IDX_SERIAL = 5

    // Google vendor ID
    const val GOOGLE_VID = 0x18D1

    // Accessory mode product IDs
    const val ACC_PID = 0x2D00
    const val ACC_ADB_PID = 0x2D01

    // AOA accessory strings
    const val MANUFACTURER = "Android"
    const val MODEL = "Android Auto"
    const val DESCRIPTION = "Android Auto"
    const val VERSION = "2.0.1"
    const val URI = ""
    const val SERIAL = ""

    // USB transfer constants
    const val USB_TIMEOUT_MS = 1000
    const val USB_DIR_IN = 0x80     // device → host
    const val USB_DIR_OUT = 0x00    // host → device
    const val USB_TYPE_VENDOR = 0x40

    /** Check if a USB device is already in Google Accessory mode. */
    fun isAccessoryDevice(vendorId: Int, productId: Int): Boolean {
        return vendorId == GOOGLE_VID && (productId == ACC_PID || productId == ACC_ADB_PID)
    }
}
