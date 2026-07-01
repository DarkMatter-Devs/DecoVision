package com.decovision.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.decovision.app.data.model.Design

/**
 * Room database for DecoVision. Version 1 — single [Design] entity.
 *
 * [FurnitureConverter] is registered here and must also be added via
 * [androidx.room.RoomDatabase.Builder.addTypeConverter] in [com.decovision.app.di.DatabaseModule].
 *
 * exportSchema = false avoids needing a schema export directory for this project.
 * // FUTURE: Set exportSchema = true and configure schemaLocation for production migrations.
 */
@Database(entities = [Design::class], version = 1, exportSchema = false)
@TypeConverters(FurnitureConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** Provides access to the [DesignDao] for CRUD operations on [Design] entities. */
    abstract fun designDao(): DesignDao
}
