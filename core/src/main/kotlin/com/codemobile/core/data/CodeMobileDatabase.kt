package com.codemobile.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.codemobile.core.data.dao.MessageDao
import com.codemobile.core.data.dao.ProjectDao
import com.codemobile.core.data.dao.ProviderConfigDao
import com.codemobile.core.data.dao.SessionDao
import com.codemobile.core.model.Message
import com.codemobile.core.model.Project
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.Session

@Database(
    entities = [
        Project::class,
        Session::class,
        Message::class,
        ProviderConfig::class
    ],
    version = 2,
    exportSchema = true
)
abstract class CodeMobileDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun providerConfigDao(): ProviderConfigDao
}
