package com.thorctl.daemon.hardware.input

/** Controller keys held and axis values (indexed by ABS_* code) at one moment; copies, safe to keep. */
class ControllerSnapshot(val keys: Set<Int>, val axisValues: IntArray)
