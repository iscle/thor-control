package com.thorctl.testing

import com.thorctl.domain.model.BatteryStatus
import com.thorctl.domain.repository.BatteryRepository

class FakeBattery(var status: BatteryStatus = BatteryStatus(64, true)) : BatteryRepository {
    override fun current() = status
}
