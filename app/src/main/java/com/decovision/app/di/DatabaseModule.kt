package com.decovision.app.di

import android.content.Context
import androidx.room.Room
import com.decovision.app.data.db.AppDatabase
import com.decovision.app.data.db.DesignDao
import com.decovision.app.data.repository.DesignRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "decovision_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideDesignDao(db: AppDatabase): DesignDao = db.designDao()

    @Provides
    @Singleton
    fun provideDesignRepository(dao: DesignDao): DesignRepository = DesignRepository(dao)
}
