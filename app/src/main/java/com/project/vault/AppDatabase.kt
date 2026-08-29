package com.project.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import com.project.vault.entity.PlaceholderEntity

/**
 * Room database for Vault.
 *
 * - Replace [PlaceholderEntity] with real feature entities as the app grows.
 * - When adding real entities: update [entities], increment [version], and
 *   provide a [androidx.room.migration.Migration] to preserve user data.
 * - Set [exportSchema] to true and configure a schema export directory via
 *   the Room Gradle plugin when you're ready to track schema history.
 * - The singleton instance is provided by Hilt via [di.AppModule].
 */
@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs will be declared here as abstract functions when entities are added.
    // Example: abstract fun itemDao(): ItemDao

    companion object {
        const val DATABASE_NAME = "vault_database"
    }
}
