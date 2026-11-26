package com.thorctl.domain.repository

import com.thorctl.domain.model.BatteryStatus

interface BatteryRepository {
    fun current(): BatteryStatus
}
