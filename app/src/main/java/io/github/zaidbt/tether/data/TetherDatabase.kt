package io.github.zaidbt.tether.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

//entities: tables,
//version: schema version (goes up whenever table changes, changed to 2 when EmergencyGrant entity was added),
//exportScheme (false): skips writing a schema JSON file
@Database(entities = [BlockRule::class, EmergencyGrant::class], version = 2, exportSchema = false)
abstract class TetherDatabase : RoomDatabase() {
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun emergencyGrantDao(): EmergencyGrantDao

    // Swap the whole rule list in one transaction so readers never see a half-empty table
    //withTransaction: rollbacks the entire transaction if error occurs midway/bw two queries here (all or nothing)
    suspend fun replaceRules(rules: List<BlockRule>) = withTransaction {
        blockRuleDao().clear()
        blockRuleDao().insertAll(rules)
    }

    //companion object can access members/funcctions of its enclosing class
    companion object {
        //Volatile: value written by one thread is immediately visible to all others...need it for check below
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
        fun get(context: Context): TetherDatabase = instance ?: synchronized(this) { //get: singleton design pattern, synchronized: thread lock
            instance ?: Room.databaseBuilder( //instance?: double-checked locking; checks again if another thread did not build the database
                //which database class to build and file name on phone memory/disk
                context.applicationContext, TetherDatabase::class.java, "tether.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it } //registers upgrade path, build it, saves new database in instance and returns it
        }
    }
}