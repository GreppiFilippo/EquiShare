/** Creates the local Room database. */
package it.unibo.equishare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedGroupEntity::class,
        CachedGroupMemberEntity::class,
        CachedGroupCategoryEntity::class,
        CachedExpenseEntity::class,
        CachedExpenseCategoryEntity::class,
        CachedActivityEntity::class,
        CachedProfileEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class EquiShareDatabase : RoomDatabase() {
    abstract fun groupDao(): CachedGroupDao
    abstract fun groupMemberDao(): CachedGroupMemberDao
    abstract fun groupCategoryDao(): CachedGroupCategoryDao
    abstract fun expenseDao(): CachedExpenseDao
    abstract fun expenseCategoryDao(): CachedExpenseCategoryDao
    abstract fun activityDao(): CachedActivityDao
    abstract fun profileDao(): CachedProfileDao

    companion object {
        fun create(context: Context): EquiShareDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                EquiShareDatabase::class.java,
                "equishare.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_group_members ADD COLUMN avatar_url TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_activities ADD COLUMN expense_id TEXT")
                db.execSQL("ALTER TABLE cached_activities ADD COLUMN payment_id TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_groups ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_expense_categories ADD COLUMN name_it TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cached_expense_categories ADD COLUMN name_en TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE cached_expense_categories SET name_it = name WHERE name_it = ''")
                db.execSQL("UPDATE cached_expense_categories SET name_en = name WHERE name_en = ''")
            }
        }
    }
}
