package com.project.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.project.vault.entity.CredentialEntity
import com.project.vault.entity.dao.CredentialDao

/**
 * Room database for Vault.
 *
 * Version history:
 *  1 → 2: Replaced stub `placeholder` table with the real `credentials` table.
 *
 * Schema conventions:
 *  - Increment [version] for every schema change.
 *  - Always provide a [Migration] to preserve user data — never use
 *    fallbackToDestructiveMigration() in production.
 *  - Set exportSchema = true and configure a schema export directory once
 *    the schema stabilises.
 *
 * The singleton instance is provided by Hilt via [di.AppModule].
 */
@Database(
    entities = [CredentialEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun credentialDao(): CredentialDao

    companion object {
        const val DATABASE_NAME = "vault_database"

        /**
         * Migration 1 → 2:
         *  - Drops the placeholder stub table.
         *  - Creates the `credentials` table with all required columns.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `placeholder`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credentials` (
                        `id`               INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `server_id`        TEXT,
                        `is_shared`        INTEGER NOT NULL DEFAULT 0,
                        `is_synced`        INTEGER NOT NULL DEFAULT 0,
                        `title`            TEXT NOT NULL,
                        `cred_type`        TEXT NOT NULL,
                        `enc_json_content` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 2 → 3:
         *  - Adds `last_synced_at` column to `credentials` table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `credentials` ADD COLUMN `last_synced_at` INTEGER")
            }
        }
    }
}
