package com.example.greenstreem

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenChannelDao {
    @Query("SELECT * FROM hidden_channels")
    fun getAllHidden(): Flow<List<HiddenChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideChannel(channel: HiddenChannel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideChannels(channels: List<HiddenChannel>)

    @Delete
    suspend fun unhideChannel(channel: HiddenChannel)

    @Query("SELECT EXISTS(SELECT * FROM hidden_channels WHERE channelId = :id)")
    suspend fun isChannelHidden(id: Long): Boolean

    @Query("DELETE FROM hidden_channels")
    suspend fun clearAll()
}
