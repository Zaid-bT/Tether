package io.github.zaidbt.tether

import kotlinx.coroutines.flow.MutableStateFlow

//temp in-memory block list for now
object BlockedApps {
    val packages = MutableStateFlow<Set<String>>(emptySet())
}

