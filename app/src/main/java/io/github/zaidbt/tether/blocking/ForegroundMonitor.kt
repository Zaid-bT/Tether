package io.github.zaidbt.tether.blocking

import kotlinx.coroutines.flow.MutableStateFlow

// Shared holder for the app currently on screen; any detector can write to it
object ForegroundMonitor { //singleton object
    //MutableStateFlow is a box holding one current value that other code can observe. When the value changes, observers are notified.
    val current = MutableStateFlow<String?>(null)
    //StateFlow ignores a write operation that equals the current value, so repeated events for the same app don't wake observers
}

