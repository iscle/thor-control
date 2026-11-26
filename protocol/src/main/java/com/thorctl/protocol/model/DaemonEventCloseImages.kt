package com.thorctl.protocol.model

/** Closes the images an event carries (for events nobody will read: their pipes are freed). */
fun DaemonEvent.closeImages() {
    when (this) {
        is DaemonEvent.RecentsOpened -> (listOf(hero, home) + tasks.map { it.thumb }).forEach { it?.close() }
        is DaemonEvent.RecentsThumb -> thumb.close()
        is DaemonEvent.HomeAnimation -> listOf(app, home).forEach { it?.close() }
        else -> {}
    }
}
