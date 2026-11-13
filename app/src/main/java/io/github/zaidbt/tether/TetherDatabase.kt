package io.github.zaidbt.tether

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BlockRule::class, EmergencyGrant::class], version = 2, exportSchema = false)
abstract class TetherDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun emergencyGrantDao(): EmergencyGrantDao

    // Swap the whole rule list in one transaction so readers never see a half-empty table
    suspend fun replaceRules(rules: List<BlockRule>) = withTransaction {
        blockRuleDao().clear()
        blockRuleDao().insertAll(rules)
    }

    companion object {
        @Volatile private var instance: TetherDatabase? = null

        // v1 -> v2: add the emergency log table, keep existing rules
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `emergency_grants` " +
                            "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`packageName` TEXT NOT NULL, `grantedAt` INTEGER NOT NULL, " +
                            "`reason` TEXT NOT NULL)"
                )
            }
        }

        // One shared database for the whole process
        fun get(context: Context): TetherDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, TetherDatabase::class.java, "tether.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}