package com.project.vault.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Placeholder entity to satisfy Room's requirement of at least one @Entity
 * in the @Database annotation during initial boilerplate setup.
 *
 * REPLACE this with real feature entities when the app's data model is defined.
 * Steps to replace:
 *  1. Delete this file.
 *  2. Add your real @Entity classes to this package.
 *  3. Update the `entities` list in [com.project.vault.AppDatabase].
 *  4. Increment `version` in AppDatabase and provide a Migration.
 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)
