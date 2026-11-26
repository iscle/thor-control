package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.hardware.InputWatcher
import com.thorctl.protocol.GamepadHid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HidReportEncoderTest {
    private val encoder = HidReportEncoder()

    private fun key(code: Int, down: Boolean = true) = encoder.onEvent(InputWatcher.EV_KEY, code, if (down) 1 else 0)

    private fun abs(code: Int, value: Int) = encoder.onEvent(InputWatcher.EV_ABS, code, value)

    private fun sync(): ByteArray = assertNotNull(encoder.onEvent(InputWatcher.EV_SYN, HidReportEncoder.SYN_REPORT, 0))

    private fun byte(report: ByteArray, index: Int) = report[index].toInt() and 0xFF

    @Test
    fun aFreshControllerIsTheNeutralReport() {
        assertContentEquals(GamepadHid.neutral(), encoder.build())
        assertEquals(GamepadHid.PAYLOAD, encoder.build().size)
    }

    @Test
    fun onlySynReportCompletesAFrame() {
        assertNull(key(InputWatcher.BTN_SOUTH))
        assertNull(abs(HidReportEncoder.ABS_X, 1000))
        assertNull(encoder.onEvent(InputWatcher.EV_SYN, 3, 0)) // SYN_DROPPED
        assertNull(encoder.onEvent(InputWatcher.EV_SW, 0, 1))
        assertEquals(GamepadHid.B2_A, byte(sync(), 2))
    }

    @Test
    fun buttonsMapToTheirBits() {
        val byte1 = mapOf(
            InputWatcher.BTN_THUMBR to GamepadHid.B1_R3,
            InputWatcher.BTN_TL2 to GamepadHid.B1_L2,
            InputWatcher.BTN_TR2 to GamepadHid.B1_R2,
            HidReportEncoder.BTN_SELECT to GamepadHid.B1_SELECT,
            HidReportEncoder.BTN_START to GamepadHid.B1_START,
            HidReportEncoder.BTN_MODE to GamepadHid.B1_HOME,
            InputWatcher.KEY_HOME to GamepadHid.B1_HOME,
            InputWatcher.KEY_BACK to GamepadHid.B1_CAPTURE,
        )
        val byte2 = mapOf(
            InputWatcher.BTN_SOUTH to GamepadHid.B2_A,
            InputWatcher.BTN_EAST to GamepadHid.B2_B,
            InputWatcher.BTN_NORTH to GamepadHid.B2_X,
            InputWatcher.BTN_WEST to GamepadHid.B2_Y,
            InputWatcher.BTN_TL to GamepadHid.B2_L1,
            InputWatcher.BTN_TR to GamepadHid.B2_R1,
            HidReportEncoder.BTN_THUMBL to GamepadHid.B2_L3,
        )
        for ((code, bit) in byte1) {
            encoder.reset()
            key(code)
            val report = sync()
            assertEquals(bit, byte(report, 1), "byte 1 for key $code")
            assertEquals(0, byte(report, 2), "byte 2 for key $code")
        }
        for ((code, bit) in byte2) {
            encoder.reset()
            key(code)
            val report = sync()
            assertEquals(0, byte(report, 1), "byte 1 for key $code")
            assertEquals(bit, byte(report, 2), "byte 2 for key $code")
        }
    }

    @Test
    fun heldButtonsCombineAndReleaseClearsThem() {
        key(InputWatcher.BTN_SOUTH)
        key(InputWatcher.BTN_EAST)
        key(HidReportEncoder.BTN_START)
        var report = sync()
        assertEquals(GamepadHid.B2_A or GamepadHid.B2_B, byte(report, 2))
        assertEquals(GamepadHid.B1_START, byte(report, 1))
        key(InputWatcher.BTN_SOUTH, down = false)
        report = sync()
        assertEquals(GamepadHid.B2_B, byte(report, 2))
        // Unknown keys (the AYN key, a d-pad button) change nothing.
        key(InputWatcher.KEY_F24)
        key(InputWatcher.BTN_DPAD_UP)
        assertContentEquals(report, sync())
    }

    @Test
    fun sticksScaleToOneTo255CentredOn128() {
        fun stick(value: Int): Int {
            abs(HidReportEncoder.ABS_X, value)
            return byte(sync(), 3)
        }
        assertEquals(128, stick(0))
        assertEquals(1, stick(-32768))
        assertEquals(1, stick(-40000))
        assertEquals(255, stick(32767))
        assertEquals(255, stick(40000))
        assertEquals(129, stick(256))
        assertEquals(127, stick(-1))
    }

    @Test
    fun eachAxisHasItsByte() {
        abs(HidReportEncoder.ABS_X, -32767)
        abs(HidReportEncoder.ABS_Y, 32767)
        abs(HidReportEncoder.ABS_RX, 256)
        abs(HidReportEncoder.ABS_RY, -256)
        abs(InputWatcher.ABS_Z, 255)
        abs(InputWatcher.ABS_RZ, 10)
        val report = sync()
        assertEquals(listOf(1, 255, 129, 127, 255, 0), (3..8).map { byte(report, it) })
        assertEquals(0, byte(report, 9))
    }

    @Test
    fun triggersHaveADeadZoneAndADigitalBitPastHalfWay() {
        fun trigger(value: Int): Pair<Int, Int> {
            abs(InputWatcher.ABS_Z, value)
            abs(InputWatcher.ABS_RZ, value)
            val report = sync()
            assertEquals(byte(report, 7), byte(report, 8))
            return byte(report, 7) to byte(report, 1)
        }
        assertEquals(0 to 0, trigger(0))
        assertEquals(0 to 0, trigger(HidReportEncoder.TRIGGER_DEAD))
        assertEquals(1 to 0, trigger(11))
        assertEquals(126 to 0, trigger(132))
        assertEquals(128 to (GamepadHid.B1_L2 or GamepadHid.B1_R2), trigger(133))
        assertEquals(255 to (GamepadHid.B1_L2 or GamepadHid.B1_R2), trigger(255))
        assertEquals(255 to (GamepadHid.B1_L2 or GamepadHid.B1_R2), trigger(1000))
    }

    @Test
    fun hatGoesClockwiseFromUp() {
        val directions = listOf(
            (0 to -1) to 0,
            (1 to -1) to 1,
            (1 to 0) to 2,
            (1 to 1) to 3,
            (0 to 1) to 4,
            (-1 to 1) to 5,
            (-1 to 0) to 6,
            (-1 to -1) to 7,
            (0 to 0) to 8,
        )
        for ((xy, hat) in directions) {
            abs(InputWatcher.ABS_HAT0X, xy.first)
            abs(InputWatcher.ABS_HAT0Y, xy.second)
            assertEquals(hat, byte(sync(), 0), "hat for $xy")
        }
    }

    @Test
    fun loadStartsFromASnapshotAndResetClearsIt() {
        key(InputWatcher.BTN_WEST)
        val axisValues = IntArray(InputWatcher.ABS_CNT)
        axisValues[HidReportEncoder.ABS_X] = 32767
        axisValues[InputWatcher.ABS_Z] = 255
        axisValues[InputWatcher.ABS_HAT0X] = 1
        encoder.load(setOf(InputWatcher.BTN_SOUTH), axisValues)
        val report = encoder.build()
        assertEquals(2, byte(report, 0))
        assertEquals(GamepadHid.B1_L2, byte(report, 1))
        assertEquals(GamepadHid.B2_A, byte(report, 2)) // Y from before the load is gone
        assertEquals(255, byte(report, 3))
        assertEquals(255, byte(report, 7))
        encoder.reset()
        assertContentEquals(GamepadHid.neutral(), encoder.build())
    }
}
