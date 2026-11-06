package io.github.zaidbt.tether

import androidx.room.Entity
import androidx.room.PrimaryKey

// One saved rule per app; limitMinutes = 0 means "always blocked" for now
@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey val packageName: String,
    val limitMinutes: Int = 0
)