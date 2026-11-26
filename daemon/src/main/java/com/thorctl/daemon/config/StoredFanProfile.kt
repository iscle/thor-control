package com.thorctl.daemon.config

import com.thorctl.protocol.model.FanPoint
import com.thorctl.protocol.model.FanProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A custom fan curve: [points] are `[temperature °C, duty %]` pairs. */
@Serializable
class StoredFanProfile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "Custom",
    @SerialName("points") val points: List<List<Float>> = emptyList(),
) {
    fun toModel() = FanProfile(id, name, points.filter { it.size >= 2 }.map { FanPoint(it[0], it[1]) })

    companion object {
        fun of(profile: FanProfile) = StoredFanProfile(profile.id, profile.name, profile.points.map { listOf(it.temp, it.percent) })
    }
}
