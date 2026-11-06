package io.github.zaidbt.tether

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    // Live list: emits again whenever the table changes
    @Query("SELECT * FROM block_rules")
    fun observeAll(): Flow<List<BlockRule>>

    // One-time read
    @Query("SELECT * FROM block_rules")
    suspend fun getAll(): List<BlockRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BlockRule>)

    @Query("DELETE FROM block_rules")
    suspend fun clear()
}