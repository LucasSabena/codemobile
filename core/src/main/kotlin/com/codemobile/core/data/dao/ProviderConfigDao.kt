package com.codemobile.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codemobile.core.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {

    @Query("SELECT * FROM provider_configs WHERE isActive = 1 ORDER BY displayName ASC")
    fun getActive(): Flow<List<ProviderConfig>>

    @Query("SELECT * FROM provider_configs ORDER BY displayName ASC")
    fun getAll(): Flow<List<ProviderConfig>>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getById(id: String): ProviderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ProviderConfig)

    @Update
    suspend fun update(config: ProviderConfig)

    @Delete
    suspend fun delete(config: ProviderConfig)
}
