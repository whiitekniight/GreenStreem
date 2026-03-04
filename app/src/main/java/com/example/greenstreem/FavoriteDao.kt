package com.example.greenstreem

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    fun getAll(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<Favorite>)

    @Delete
    suspend fun delete(favorite: Favorite)

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE streamId = :id)")
    suspend fun isFavorite(id: Int): Boolean

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
