package com.thorctl.daemon.hardware.input

import java.util.concurrent.CopyOnWriteArrayList

/** Records listener calls by name ("tap", "combo:B", "homeDown", ...). */
class RecordingListener : InputWatcher.Listener {
    val calls = CopyOnWriteArrayList<String>()

    override fun onAynTap() {
        calls += "tap"
    }

    override fun onCombo(combo: AynCombo) {
        calls += "combo:$combo"
    }

    override fun onHomeDown() {
        calls += "homeDown"
    }

    override fun onHomeSingle() {
        calls += "homeSingle"
    }

    override fun onShoulder() {
        calls += "shoulder"
    }

    override fun onHomeLong() {
        calls += "homeLong"
    }
}
