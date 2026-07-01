package com.decovision.app.data.repository

import com.decovision.app.data.db.DesignDao
import com.decovision.app.data.model.Design
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository that abstracts all data operations for [Design] entities.
 * ViewModels interact exclusively with this class — never directly with the DAO.
 *
 * // FUTURE: Add a remote data source here for Firebase Firestore cloud sync.
 */
class DesignRepository @Inject constructor(private val dao: DesignDao) {

    /**
     * Returns a [Flow] of all saved designs ordered by creation time (newest first).
     */
    fun getAllDesigns(): Flow<List<Design>> = dao.getAllDesigns()

    /**
     * Returns a [Flow] of the 3 most recent designs for the Home screen preview strip.
     */
    fun getRecentDesigns(): Flow<List<Design>> = dao.getRecentDesigns()

    /**
     * Inserts or replaces a [design] in the local Room database.
     * Must be called from a coroutine context with [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun insertDesign(design: Design) = dao.insertDesign(design)

    /**
     * Permanently deletes the given [design] from the local Room database.
     * Must be called from a coroutine context with [kotlinx.coroutines.Dispatchers.IO].
     */
    suspend fun deleteDesign(design: Design) = dao.deleteDesign(design)
}
