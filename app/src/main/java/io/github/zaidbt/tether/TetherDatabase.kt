package io.github.zaidbt.tether

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

@Database(entities = [BlockRule::class], version = 1, exportSchema = false)
abstract class TetherDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao

    // Swap the whole rule list in one transaction so readers never see a half-empty table
    suspend fun replaceRules(rules: List<BlockRule>) = withTransaction {
        blockRuleDao().clear()
        blockRuleDao().insertAll(rules)
    }

    companion object {
        @Volatile private var instance: TetherDatabase? = null

        // One shared database for the whole process
        fun get(context: Context): TetherDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, TetherDatabase::class.java, "tether.db"
            ).build().also { instance = it }
        }
    }
}