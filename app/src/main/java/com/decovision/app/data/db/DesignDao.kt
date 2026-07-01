package com.decovision.app.data.db

import androidx.room.*
import com.decovision.app.data.model.Design
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for [Design] entities.
 * All queries return [Flow] for reactive UI updates, except mutations which are suspend functions.
 */
@Dao
interface DesignDao {

    /**
     * Returns all designs ordered by creation time (most recent first).
     * Emits a new list whenever the underlying table changes.
     */
    @Query("SELECT * FROM designs ORDER BY createdAt DESC")
    fun getAllDesigns(): Flow<List<Design>>

    /**
     * Returns the 3 most recently created designs for the Home screen preview.
     * Emits a new list whenever the underlying table changes.
     */
    @Query("SELECT * FROM designs ORDER BY createdAt DESC LIMIT 3")
    fun getRecentDesigns(): Flow<List<Design>>

    /**
     * Inserts or replaces a design. Use [OnConflictStrategy.REPLACE] to handle re-saves.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesign(design: Design)

    /**
     * Permanently deletes the given [design] from the database.
     */
    @Delete
    suspend fun deleteDesign(design: Design)
}
