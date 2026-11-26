package com.thorctl.protocol

/**
 * The gamepad as hosts see it: a Google Stadia Controller (USB vendor 18D1, product 9400,
 * version 1.00) with the Stadia's report descriptor and report layout.
 *
 * A generic HID gamepad works in SDL, browsers and on Linux, but macOS only hands controllers to
 * games (the GameController framework, System Settings > Game Controllers) when it knows the
 * vendor and product ID. The Stadia controller is a plain HID gamepad, with no handshake, that
 * macOS, SDL (its HIDAPI driver), Chrome, Linux and Android all know. It uses Google's vendor
 * ID, which the Thor's USB already has. Over Bluetooth the same IDs come from the Thor's Device
 * ID record (`bluetooth.device_id.*` in magisk/system.prop).
 *
 * Input report 3, 10 bytes after the report ID:
 *  - [0] D-pad hat, 0 = up, clockwise, 8 = centred.
 *  - [1] bit 0 Capture (Share on macOS), 1 Assistant, 2 L2 and 3 R2 (digital), 4 Home,
 *        5 Menu (Start), 6 Options (Select), 7 R3.
 *  - [2] bit 0 L3, 1 R1, 2 L1, 3 Y, 4 X, 5 B, 6 A.
 *  - [3..6] left stick X / Y, right stick X / Y, 1..255 with 128 centred.
 *  - [7] L2 and [8] R2, analog 0..255.
 *  - [9] volume up / down / play (unused).
 *
 * Button usages follow Android: A = 1, B = 2, X = 4, Y = 5, L1 = 7, R1 = 8, Select = 11,
 * Start = 12, Home = 13, L3 = 14, R3 = 15, so Linux hosts get the usual BTN_* codes. Home and
 * Select carry each other's usage relative to the bits SDL reads, which is what makes all three of
 * macOS, SDL and Linux agree (checked on a Mac: Home opens Games, Select is Options).
 *
 * Output report 5 (rumble, two 16-bit motors) is declared because Linux's Stadia driver needs
 * it. Hosts may send it; it is read and dropped.
 */
object GamepadHid {
    const val VENDOR_ID = 0x18D1
    const val PRODUCT_ID = 0x9400
    const val VERSION = 0x0100

    const val REPORT_ID = 3

    /** Bytes after the report ID. */
    const val PAYLOAD = 10

    // Bits of byte 1 and byte 2.
    const val B1_CAPTURE = 0x01
    const val B1_L2 = 0x04
    const val B1_R2 = 0x08
    const val B1_HOME = 0x10
    const val B1_START = 0x20
    const val B1_SELECT = 0x40
    const val B1_R3 = 0x80
    const val B2_L3 = 0x01
    const val B2_R1 = 0x02
    const val B2_L1 = 0x04
    const val B2_Y = 0x08
    const val B2_X = 0x10
    const val B2_B = 0x20
    const val B2_A = 0x40

    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Game Pad)
        0xA1, 0x01, // Collection (Application)
        0x85, REPORT_ID, //   Report ID (3)
        0x05, 0x01, //   Usage Page (Generic Desktop)
        0x15, 0x00, //   Logical Minimum (0)
        0x75, 0x04, //   Report Size (4)
        0x95, 0x01, //   Report Count (1)
        0x25, 0x07, //   Logical Maximum (7)
        0x46, 0x3B, 0x01, //   Physical Maximum (315)
        0x65, 0x14, //   Unit (Degrees)
        0x09, 0x39, //   Usage (Hat Switch)
        0x81, 0x42, //   Input (Data, Var, Abs, Null State)
        0x45, 0x00, //   Physical Maximum (0)
        0x65, 0x00, //   Unit (None)
        0x75, 0x01, //   Report Size (1)
        0x95, 0x04, //   Report Count (4)
        0x81, 0x01, //   Input (Const): padding
        0x05, 0x09, //   Usage Page (Button)
        0x15, 0x00, //   Logical Minimum (0)
        0x25, 0x01, //   Logical Maximum (1)
        0x75, 0x01, //   Report Size (1)
        0x95, 0x0F, //   Report Count (15), byte 1 bit 0 to byte 2 bit 6:
        0x09, 0x12, //     Button 18: Capture
        0x09, 0x11, //     Button 17: Assistant
        0x09, 0x14, //     Button 20: L2
        0x09, 0x13, //     Button 19: R2
        0x09, 0x0D, //     Button 13: Home
        0x09, 0x0C, //     Button 12: Start
        0x09, 0x0B, //     Button 11: Select
        0x09, 0x0F, //     Button 15: R3
        0x09, 0x0E, //     Button 14: L3
        0x09, 0x08, //     Button 8: R1
        0x09, 0x07, //     Button 7: L1
        0x09, 0x05, //     Button 5: Y
        0x09, 0x04, //     Button 4: X
        0x09, 0x02, //     Button 2: B
        0x09, 0x01, //     Button 1: A
        0x81, 0x02, //   Input (Data, Var, Abs)
        0x75, 0x01, //   Report Size (1)
        0x95, 0x01, //   Report Count (1)
        0x81, 0x01, //   Input (Const): padding
        0x05, 0x01, //   Usage Page (Generic Desktop)
        0x15, 0x01, //   Logical Minimum (1)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x09, 0x01, //   Usage (Pointer)
        0xA1, 0x00, //   Collection (Physical)
        0x09, 0x30, //     Usage (X)
        0x09, 0x31, //     Usage (Y)
        0x75, 0x08, //     Report Size (8)
        0x95, 0x02, //     Report Count (2)
        0x81, 0x02, //     Input (Data, Var, Abs)
        0xC0, //   End Collection
        0x09, 0x01, //   Usage (Pointer)
        0xA1, 0x00, //   Collection (Physical)
        0x09, 0x32, //     Usage (Z)
        0x09, 0x35, //     Usage (Rz)
        0x75, 0x08, //     Report Size (8)
        0x95, 0x02, //     Report Count (2)
        0x81, 0x02, //     Input (Data, Var, Abs)
        0xC0, //   End Collection
        0x05, 0x02, //   Usage Page (Simulation Controls)
        0x75, 0x08, //   Report Size (8)
        0x95, 0x02, //   Report Count (2)
        0x15, 0x00, //   Logical Minimum (0)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x09, 0xC5, //   Usage (Brake): L2
        0x09, 0xC4, //   Usage (Accelerator): R2
        0x81, 0x02, //   Input (Data, Var, Abs)
        0x05, 0x0C, //   Usage Page (Consumer)
        0x15, 0x00, //   Logical Minimum (0)
        0x25, 0x01, //   Logical Maximum (1)
        0x09, 0xE9, //   Usage (Volume Increment)
        0x09, 0xEA, //   Usage (Volume Decrement)
        0x75, 0x01, //   Report Size (1)
        0x95, 0x02, //   Report Count (2)
        0x81, 0x02, //   Input (Data, Var, Abs)
        0x09, 0xCD, //   Usage (Play/Pause)
        0x95, 0x01, //   Report Count (1)
        0x81, 0x02, //   Input (Data, Var, Abs)
        0x95, 0x05, //   Report Count (5)
        0x81, 0x01, //   Input (Const): padding
        0x85, 0x05, //   Report ID (5)
        0x06, 0x0F, 0x00, //   Usage Page (Physical Interface Device)
        0x09, 0x97, //   Usage (DC Enable Actuators)
        0x75, 0x10, //   Report Size (16)
        0x95, 0x02, //   Report Count (2)
        0x27, 0xFF, 0xFF, 0x00, 0x00, //   Logical Maximum (65535)
        0x91, 0x02, //   Output (Data, Var, Abs): rumble, strong and weak motor
        0xC0, // End Collection
    ).map { it.toByte() }.toByteArray()

    /** Everything released, sticks centred. */
    fun neutral(): ByteArray = ByteArray(PAYLOAD).also {
        it[0] = 8
        for (stick in 3..6) it[stick] = 128.toByte()
    }
}
