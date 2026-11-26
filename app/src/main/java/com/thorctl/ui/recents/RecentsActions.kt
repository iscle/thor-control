package com.thorctl.ui.recents

/** What the stage asks the controller to do. */
interface RecentsActions {
    /** Zooming into an app card (start of the animation). */
    fun openApp(card: Card)

    /** Zooming into the home card; [alreadyThere] when the home screen is what's underneath. */
    fun openHome(alreadyThere: Boolean)

    /** Zooming back into the app recents was opened from (it is still underneath). */
    fun back()
    fun remove(card: Card)
    fun clearAll(ids: List<Int>)

    /** Home press: the overlay now covers the app, switch to the home screen underneath. */
    fun homeGo()

    /** Zoom finished; remove the overlay once the switch underneath has settled. */
    fun finish(settleMs: Long)
    fun tick()
}
