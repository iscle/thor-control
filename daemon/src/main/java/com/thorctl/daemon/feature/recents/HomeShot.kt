package com.thorctl.daemon.feature.recents

import com.thorctl.daemon.system.AppTask
import java.io.File

/** The screen recents opens from: the app it showed (null: the home screen) and its picture, if one was taken. */
data class HomeShot(val origin: AppTask?, val picture: File?)
