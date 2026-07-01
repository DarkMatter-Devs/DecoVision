package com.decovision.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved AR interior design.
 *
 * [furnitureJson] stores a Gson-serialized [List] of [FurnitureItem] objects.
 * The [FurnitureConverter] TypeConverter handles serialization/deserialization.
 *
 * // FUTURE: Add a roomId field for multi-room support once multi-room AR scanning is implemented.
 * // FUTURE: Add a cloudSyncId for Firebase Firestore cloud backup integration.
 */
@Entity(tableName = "designs")
data class Design(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Human-readable name for this design, e.g. "Living Room v2" */
    val name: String,
    /** Absolute file path to the thumbnail image — never a URI string */
    val thumbnailPath: String,
    /** Gson-serialized List<FurnitureItem> */
    val furnitureJson: String,
    /** Unix timestamp: System.currentTimeMillis() at save time */
    val createdAt: Long
)
