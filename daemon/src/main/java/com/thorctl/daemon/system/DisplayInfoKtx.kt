package com.thorctl.daemon.system

import java.lang.reflect.Field

/** android.view.DisplayInfo (hidden; public fields), resolved once. */
private val displayInfoClass: Class<*> by lazy { Class.forName("android.view.DisplayInfo") }
private val uniqueIdField: Field by lazy { displayInfoClass.getField("uniqueId") }
private val typeField: Field by lazy { displayInfoClass.getField("type") }
private val logicalWidthField: Field by lazy { displayInfoClass.getField("logicalWidth") }
private val logicalHeightField: Field by lazy { displayInfoClass.getField("logicalHeight") }
private val stateField: Field by lazy { displayInfoClass.getField("state") }

/** This android.view.DisplayInfo as our [DisplayInfo]. */
internal fun Any.toDisplayInfo(displayId: Int) = DisplayInfo(
    id = displayId,
    uniqueId = uniqueIdField.get(this) as? String ?: "",
    type = typeField.getInt(this),
    width = logicalWidthField.getInt(this),
    height = logicalHeightField.getInt(this),
    state = stateField.getInt(this),
)
