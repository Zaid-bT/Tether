package io.github.zaidbt.tether.blocking

import kotlinx.coroutines.flow.MutableStateFlow

// In-memory copy of the saved rules: package name -> daily limit in minutes (0 = always blocked)
object BlockedApps { //singleton object

    //Map<String, Int> maps package name to daily limit in minutes
    //Map lookup is direct and faster and no duplicates
    val limits = MutableStateFlow<Map<String, Int>>(emptyMap())
    //write by: TetherAccessibilityService (line 41)
    //read by: TetherAccessibilityService (line 74)
}