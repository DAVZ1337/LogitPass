package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credentials ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllCredentials(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials")
    suspend fun getAllCredentialsList(): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getCredentialById(id: String): CredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: CredentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(credentials: List<CredentialEntity>)

    @Update
    suspend fun update(credential: CredentialEntity)

    @Delete
    suspend fun delete(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()

    @Query("UPDATE credentials SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
}
