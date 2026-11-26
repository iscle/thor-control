package com.thorctl.app

import android.content.Context
import com.thorctl.di.AppGraph

/** The graph, from any Android component of this app. */
val Context.graph: AppGraph get() = (applicationContext as ThorApp).graph
