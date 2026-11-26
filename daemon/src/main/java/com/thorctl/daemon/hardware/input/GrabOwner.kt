package com.thorctl.daemon.hardware.input

/** Who holds the exclusive controller grab (see [ControllerInput.beginGrab]); one at a time. */
enum class GrabOwner { NONE, AYN, RECENTS, GAMEPAD }
