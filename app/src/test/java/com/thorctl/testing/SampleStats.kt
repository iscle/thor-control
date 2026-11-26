package com.thorctl.testing

import com.thorctl.protocol.model.FanSpeed
import com.thorctl.protocol.model.Stats

val sampleStats = Stats(
    0.42f,
    2400,
    listOf(1800, 2400),
    61.4f,
    0.1f,
    500,
    48f,
    7680,
    3072,
    81,
    "Discharging",
    6.25f,
    0f,
    31f,
    FanSpeed.pwmFor(50f),
    58f
)
