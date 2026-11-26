package com.thorctl.app

import com.thorctl.di.AppGraph

/** The graph, from any Android component of this app. */
val android.content.Context.graph: AppGraph get() = (applicationContext as ThorApp).graph
