package io.github.zaidbt.tether.blocking

import kotlinx.coroutines.flow.MutableStateFlow

// In-memory copy of the saved rules: package name -> daily limit in minutes (0 = always blocked)
object BlockedApps {
    val limits = MutableStateFlow<Map<String, Int>>(emptyMap())
}