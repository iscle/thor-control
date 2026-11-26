package com.thorctl.daemon.hardware.input

import com.thorctl.daemon.di.DaemonScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** [Evdev] on the real /dev/input nodes: struct input_event over plain file streams, ioctls for grab and switches. */
@Inject
@ContributesBinding(DaemonScope::class)
class KernelEvdev : Evdev {
    override fun open(path: String): EvdevDevice = NodeDevice(FileInputStream(path))

    override fun write(path: String, events: List<EvdevEvent>) {
        val buffer = ByteBuffer.allocate(EVENT_SIZE * events.size).order(ByteOrder.LITTLE_ENDIAN)
        for (event in events) {
            buffer.putLong(0L).putLong(0L).putShort(event.type.toShort()).putShort(event.code.toShort()).putInt(event.value)
        }
        FileOutputStream(path).use { it.write(buffer.array()) }
    }

    private class NodeDevice(private val stream: FileInputStream) : EvdevDevice {
        override fun readEvents(onEvent: EventHandler) {
            val bytes = ByteArray(EVENT_SIZE * 64)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                val bytesRead = stream.read(bytes)
                if (bytesRead <= 0) return
                var offset = 0
                while (offset + EVENT_SIZE <= bytesRead) {
                    onEvent(buffer.getShort(offset + 16).toInt() and 0xFFFF, buffer.getShort(offset + 18).toInt() and 0xFFFF, buffer.getInt(offset + 20))
                    offset += EVENT_SIZE
                }
            }
        }

        override fun grab() {
            ioctlInt(stream.fd, EVIOCGRAB)
        }

        override fun switches(): Int = ioctlInt(stream.fd, EVIOCGSW_4)

        override fun close() = stream.close()
    }

    private companion object {
        const val EVENT_SIZE = 24 // struct input_event on a 64-bit kernel

        /** _IOW('E', 0x90, int) */
        const val EVIOCGRAB = 0x40044590

        /** _IOC(_IOC_READ, 'E', 0x1b, 4): the first 32 switch bits. */
        const val EVIOCGSW_4 = 0x8004451B.toInt()
    }
}
