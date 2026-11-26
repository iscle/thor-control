package com.thorctl.daemon.feature

import com.thorctl.daemon.feature.gamemode.GameMode as MovedGameMode

/**
 * Game mode moved to the gamemode package. feature/Recents.kt still names it without an import
 * (it used to share this package); this alias keeps it compiling until it imports the new one.
 */
typealias GameMode = MovedGameMode
