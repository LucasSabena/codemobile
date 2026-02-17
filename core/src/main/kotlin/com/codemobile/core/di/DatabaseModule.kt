package com.codemobile.core.di

import android.content.Context
import androidx.room.Room
import com.codemobile.core.data.CodeMobileDatabase
import com.codemobile.core.data.dao.MessageDao
import com.codemobile.core.data.dao.ProjectDao
import com.codemobile.core.data.dao.ProviderConfigDao
import com.codemobile.core.data.dao.SessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): CodeMobileDatabase {
        return Room.databaseBuilder(
            context,
            CodeMobileDatabase::class.java,
            "codemobile.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(db: CodeMobileDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideSessionDao(db: CodeMobileDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: CodeMobileDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideProviderConfigDao(db: CodeMobileDatabase): ProviderConfigDao = db.providerConfigDao()
}
