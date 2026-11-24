package io.github.zaidbt.tether.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

//DAO = Data Access Object, abstract class defines methods use to interact with database table
//      instead of writing SQL queries; Room generates the working code at build time
@Dao
interface BlockRuleDao { //interface : only function signatures, Room writes the real implementation

    // Live list: emits again whenever the table changes
    @Query("SELECT * FROM block_rules")
    //Flow is an asynchronous data stream built on coroutines that emits multiple values sequentially over time
    //whenever table changes, Flow automatically queries the updated rows and emits a new List<BlockRule>
    //how accessibility service learns about a save without being told
    fun observeAll(): Flow<List<BlockRule>>

    // One-time read (same query but returns list once only)
    @Query("SELECT * FROM block_rules")
    suspend fun getAll(): List<BlockRule> //suspend allows function to pause without freezing the UI

    //stores each rule in list
    @Insert(onConflict = OnConflictStrategy.REPLACE) //REPLACE: old row is overwritten instead of crashing
    suspend fun insertAll(rules: List<BlockRule>)

    @Query("DELETE FROM block_rules")
    suspend fun clear()
}