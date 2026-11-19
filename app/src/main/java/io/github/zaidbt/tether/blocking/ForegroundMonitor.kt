package io.github.zaidbt.tether.blocking

import kotlinx.coroutines.flow.MutableStateFlow

// Shared holder for the app currently on screen; any detector can write to it
object ForegroundMonitor {
    val current = MutableStateFlow<String?>(null)
}

