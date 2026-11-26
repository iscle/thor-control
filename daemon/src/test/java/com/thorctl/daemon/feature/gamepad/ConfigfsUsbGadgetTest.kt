package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.FakeSys
import com.thorctl.daemon.feature.gamepad.ConfigfsUsbGadget.Companion.FUNCTION
import com.thorctl.daemon.feature.gamepad.ConfigfsUsbGadget.Companion.GADGET
import com.thorctl.daemon.feature.gamepad.ConfigfsUsbGadget.Companion.USB_IDS
import com.thorctl.protocol.GamepadHid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigfsUsbGadgetTest {
    private val udc = "a600000.dwc3"
    private val config = "$GADGET/configs/b.1"
    private val link = "$config/${ConfigfsUsbGadget.LINK_NAME}"
    private val udcState = "${ConfigfsUsbGadget.UDC_CLASS}/$udc/state"

    /** Android's gadget with adb, bound, a computer attached. */
    private val sys = FakeSys().apply {
        this["$GADGET/UDC"] = udc
        this["$GADGET/idVendor"] = "0x18d1"
        this["$GADGET/idProduct"] = "0x4ee7"
        this["$GADGET/bcdDevice"] = "0x0510"
        this["$config/strings/0x409/configuration"] = "adb"
        this["$GADGET/functions/ffs.adb/ready"] = 1
        links["$config/f1"] = "$GADGET/functions/ffs.adb"
        this[udcState] = "configured"
    }
    private val gadget = ConfigfsUsbGadget(sys)

    @Test
    fun setUpCreatesTheFunctionTakesTheIdsLinksAndRebinds() {
        assertNull(gadget.addHidFunction())
        assertEquals(
            listOf(
                "$FUNCTION/protocol" to "0",
                "$FUNCTION/subclass" to "0",
                "$FUNCTION/report_length" to "11",
                "$FUNCTION/report_desc" to String(GamepadHid.DESCRIPTOR, Charsets.ISO_8859_1),
                "$GADGET/UDC" to "\n",
                USB_IDS to "0x18d1\n0x4ee7\n0x0510",
                "$GADGET/idVendor" to "0x18d1",
                "$GADGET/idProduct" to "0x9400",
                "$GADGET/bcdDevice" to "0x0100",
                "$GADGET/UDC" to udc,
            ),
            sys.writes,
        )
        assertContentEquals(GamepadHid.DESCRIPTOR, sys.bytes("$FUNCTION/report_desc"))
        assertTrue(FUNCTION in sys.directories)
        assertEquals(FUNCTION, sys.links[link])
        assertTrue(gadget.isLinked())
        assertTrue(gadget.hasLeftovers())
    }

    @Test
    fun setUpDoesNothingWhenAlreadyLinked() {
        sys.links[link] = FUNCTION
        assertNull(gadget.addHidFunction())
        assertEquals(emptyList(), sys.writes)
    }

    @Test
    fun tearDownUnlinksRestoresTheIdsAndRebinds() {
        gadget.addHidFunction()
        sys.writes.clear()
        gadget.removeHidFunction()
        assertEquals(
            listOf(
                "$GADGET/UDC" to "\n",
                "$GADGET/idVendor" to "0x18d1",
                "$GADGET/idProduct" to "0x4ee7",
                "$GADGET/bcdDevice" to "0x0510",
                "$GADGET/UDC" to udc,
            ),
            sys.writes,
        )
        assertEquals(listOf(link, USB_IDS, FUNCTION), sys.removals)
        assertFalse(link in sys.links)
        assertFalse(sys.exists(USB_IDS))
        assertFalse(gadget.isLinked())
        assertFalse(gadget.hasLeftovers())
        // adb's own link is untouched.
        assertTrue("$config/f1" in sys.links)
    }

    @Test
    fun tearDownWithNothingLeftOnlyRemovesTheFunctionDirectory() {
        assertFalse(gadget.hasLeftovers())
        gadget.removeHidFunction()
        assertEquals(emptyList(), sys.writes)
        assertEquals(listOf(FUNCTION), sys.removals)
    }

    @Test
    fun savedIdsAloneAreALeftoverThatTearDownRestores() {
        sys[USB_IDS] = "0x18d1\n0x4ee7\n0x0510\n"
        sys["$GADGET/idProduct"] = "0x9400"
        assertTrue(gadget.hasLeftovers())
        gadget.removeHidFunction()
        assertEquals("0x4ee7", sys["$GADGET/idProduct"])
        assertFalse(sys.exists(USB_IDS))
        assertEquals(udc, sys["$GADGET/UDC"])
    }

    @Test
    fun malformedSavedIdsAreDroppedWithoutWriting() {
        sys[USB_IDS] = "0x18d1\ngarbage"
        gadget.removeHidFunction()
        assertEquals("0x4ee7", sys["$GADGET/idProduct"])
        assertEquals(listOf("$GADGET/UDC" to "\n", "$GADGET/UDC" to udc), sys.writes)
        assertFalse(sys.exists(USB_IDS))
    }

    @Test
    fun androidsIdsAreSavedOnlyOnce() {
        sys[USB_IDS] = "0x1111\n0x2222\n0x0001"
        gadget.addHidFunction()
        assertEquals("0x1111\n0x2222\n0x0001", sys[USB_IDS])
        assertTrue(USB_IDS !in sys.writes.map { it.first })
    }

    @Test
    fun theGamepadsOwnIdsAreNeverSavedAsAndroids() {
        sys["$GADGET/idProduct"] = "0x9400"
        gadget.addHidFunction()
        assertFalse(sys.exists(USB_IDS))
    }

    @Test
    fun anUnboundGadgetUsesTheFirstUsbController() {
        sys["$GADGET/UDC"] = ""
        assertNull(gadget.addHidFunction())
        assertEquals(udc, sys["$GADGET/UDC"])
        assertTrue(gadget.isConfigured())
    }

    @Test
    fun setUpErrors() {
        assertEquals("USB gadget not available", ConfigfsUsbGadget(FakeSys()).addHidFunction())
        val noConfig = FakeSys().apply { this["$GADGET/UDC"] = udc }
        assertEquals("USB gadget has no configuration", ConfigfsUsbGadget(noConfig).addHidFunction())
        sys["$GADGET/UDC"] = ""
        sys.files.remove(udcState)
        assertEquals("No USB controller", gadget.addHidFunction())
        assertFalse(gadget.isLinked())
    }

    @Test
    fun aFailedLinkGivesAndroidItsIdsBackAndRebinds() {
        sys[link] = "in the way"
        assertEquals("USB HID set-up failed", gadget.addHidFunction())
        assertEquals("0x4ee7", sys["$GADGET/idProduct"])
        assertFalse(sys.exists(USB_IDS))
        assertEquals(udc, sys["$GADGET/UDC"])
    }

    @Test
    fun configuredFollowsTheControllersState() {
        assertTrue(gadget.isConfigured())
        sys[udcState] = "not attached"
        assertFalse(gadget.isConfigured())
    }
}
