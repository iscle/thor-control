package com.thorctl.protocol.model

/** What game mode does while it is on. */
data class GameOptions(
    /** Close other apps when the game starts and keep background apps closed while it runs. */
    val closeApps: Boolean = true,
    /** Hold every CPU core online at its top frequency. */
    val cpu: Boolean = true,
    /** Hold the GPU at its top frequency. */
    val gpu: Boolean = true,
    /** Fan profile while boosting; empty keeps the selected profile. */
    val fan: String = "sport",
    /** Turn the bottom screen off while the game is in front (the launcher there then frees its memory). */
    val bottomOff: Boolean = true,
)
