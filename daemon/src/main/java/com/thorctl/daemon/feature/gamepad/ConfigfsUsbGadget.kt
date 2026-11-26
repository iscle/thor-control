package com.thorctl.daemon.feature.gamepad

import com.thorctl.daemon.core.Sys
import com.thorctl.daemon.di.DaemonScope
import com.thorctl.protocol.GamepadHid
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import timber.log.Timber

/**
 * [UsbGadget] on configfs (/config/usb_gadget/g1): an f_hid function is created, linked into the
 * gadget's configuration and the gadget rebound; the gadget takes the gamepad's IDs meanwhile, so
 * a computer sees a composite device with a gamepad.
 */
@Inject
@ContributesBinding(DaemonScope::class)
class ConfigfsUsbGadget(private val sys: Sys) : UsbGadget {
    override fun isLinked(): Boolean {
        val configDirectory = configDir() ?: return false
        return !sys.readLink("$configDirectory/$LINK_NAME").isNullOrEmpty()
    }

    override fun hasLeftovers(): Boolean = isLinked() || sys.exists(USB_IDS)

    override fun addHidFunction(): String? {
        if (!sys.isDirectory(GADGET)) return "USB gadget not available"
        val configDirectory = configDir() ?: return "USB gadget has no configuration"
        if (isLinked()) return null
        if (!sys.isDirectory(FUNCTION) && !sys.mkdirs(FUNCTION)) return "This kernel has no USB HID gadget support"
        val written = sys.write("$FUNCTION/protocol", "0") &&
            sys.write("$FUNCTION/subclass", "0") &&
            sys.write("$FUNCTION/report_length", (1 + GamepadHid.PAYLOAD).toString()) &&
            sys.writeBytes("$FUNCTION/report_desc", GamepadHid.DESCRIPTOR)
        if (!written) {
            Timber.w("gamepad: HID function set-up failed")
            return "USB HID set-up failed"
        }
        val udc = udcName()
        if (udc.isEmpty()) return "No USB controller"
        // The configuration can only change while unbound. Android's USB HAL rebinds the gadget
        // by itself when adb comes back; any bind after the link includes the gamepad.
        unbind()
        setGamepadIds()
        if (!sys.symlink(FUNCTION, "$configDirectory/$LINK_NAME")) {
            Timber.w("gamepad: linking the HID function failed")
            restoreIds()
            bind(udc)
            return "USB HID set-up failed"
        }
        bind(udc)
        Timber.i("gamepad: USB HID function added (${configDirectory.substringAfterLast('/')}, $udc)")
        return null
    }

    override fun removeHidFunction() {
        val configDirectory = configDir() ?: return
        val link = "$configDirectory/$LINK_NAME"
        val udc = udcName()
        if (isLinked() || sys.exists(USB_IDS)) {
            unbind()
            if (isLinked() && !sys.remove(link)) Timber.w("gamepad: unlinking the HID function failed")
            restoreIds()
            if (udc.isNotEmpty()) bind(udc)
        }
        sys.remove(FUNCTION) // rmdir; fails harmlessly if something still links it
        Timber.i("gamepad: USB HID function removed")
    }

    override fun isConfigured(): Boolean = sys.read("$UDC_CLASS/${udcName()}/state")?.trim() == "configured"

    /** The controller the gadget is bound to, or else the first one there is ("" if none). */
    private fun udcName(): String =
        sys.read("$GADGET/UDC")?.trim()?.takeIf { it.isNotEmpty() }
            ?: sys.list(UDC_CLASS).firstOrNull()?.substringAfterLast('/')
            ?: ""

    private fun configDir(): String? = sys.list("$GADGET/configs").firstOrNull { sys.isDirectory(it) }

    /**
     * The whole gadget (adb included) takes the gamepad's IDs; macOS only accepts gamepads it
     * knows by ID. Android's own IDs are kept in [USB_IDS] (also across a daemon crash).
     */
    private fun setGamepadIds() {
        val currentIds = USB_ID_ATTRIBUTES.map { sys.read("$GADGET/$it") ?: "" }
        val productId = currentIds[1].removePrefix("0x").toIntOrNull(16)
        if (!sys.exists(USB_IDS) && productId != null && productId != GamepadHid.PRODUCT_ID) sys.write(USB_IDS, currentIds.joinToString("\n"))
        sys.write("$GADGET/idVendor", hex(GamepadHid.VENDOR_ID))
        sys.write("$GADGET/idProduct", hex(GamepadHid.PRODUCT_ID))
        sys.write("$GADGET/bcdDevice", hex(GamepadHid.VERSION))
    }

    /** Android's IDs back (while unbound). */
    private fun restoreIds() {
        val saved = sys.read(USB_IDS)?.lines() ?: return
        if (saved.size == USB_ID_ATTRIBUTES.size && saved.all { it.startsWith("0x") }) {
            USB_ID_ATTRIBUTES.forEachIndexed { index, attribute -> sys.write("$GADGET/$attribute", saved[index]) }
        }
        sys.remove(USB_IDS)
    }

    private fun hex(value: Int) = "0x%04x".format(value)

    private fun unbind() {
        sys.write("$GADGET/UDC", "\n")
        Thread.sleep(100)
    }

    /** Binds the gadget, unless Android's USB HAL already did (it reacts to adb reopening). */
    private fun bind(udc: String) {
        repeat(40) {
            if (!sys.read("$GADGET/UDC")?.trim().isNullOrEmpty()) return
            sys.write("$GADGET/UDC", udc)
            Thread.sleep(100)
        }
        Timber.w("gamepad: USB gadget did not bind")
    }

    companion object {
        const val GADGET = "/config/usb_gadget/g1"
        const val UDC_CLASS = "/sys/class/udc"
        const val FUNCTION = "$GADGET/functions/hid.thorpad"

        /** Not "functionN": Android's USB HAL unlinks those when it rebuilds the configuration. */
        const val LINK_NAME = "thor_gamepad"

        /** Android's USB IDs while the gadget has the gamepad's. */
        const val USB_IDS = "/data/adb/thorctl/usb-ids"
        val USB_ID_ATTRIBUTES = listOf("idVendor", "idProduct", "bcdDevice")
    }
}
