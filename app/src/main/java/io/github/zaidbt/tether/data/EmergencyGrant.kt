//file holds log of emergency unlocks and unlock count

package io.github.zaidbt.tether.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

// One row per emergency unlock used (kept as a log)
@Entity(tableName = "emergency_grants")
data class EmergencyGrant(
    //matches exactly to the SQL in MIGRATION_1_2
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long, // wall-clock millis (phone clock)
    val reason: String
)

@Dao
interface EmergencyGrantDao {
    @Insert
    suspend fun insert(grant: EmergencyGrant) //autoGenerate id never collides so no collision handling

    // How many unlocks were used since a given time (used for the daily allowance)
    // how many emergency unlocks since midnight
    @Query("SELECT COUNT(*) FROM emergency_grants WHERE grantedAt >= :since")
    suspend fun countSince(since: Long): Int
}