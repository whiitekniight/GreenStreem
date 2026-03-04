package com.example.greenstreem

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM hidden_groups")
    fun getAllHidden(): Flow<List<HiddenGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideGroup(group: HiddenGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideGroups(groups: List<HiddenGroup>)

    @Delete
    suspend fun showGroup(group: HiddenGroup)

    @Query("SELECT EXISTS(SELECT * FROM hidden_groups WHERE groupId = :id)")
    suspend fun isGroupHidden(id: String): Boolean

    @Query("DELETE FROM hidden_groups")
    suspend fun clearAll()
}
