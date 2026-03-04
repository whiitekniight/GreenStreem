package com.example.greenstreem

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelOrderDao {
    @Query("SELECT * FROM channel_order ORDER BY groupId ASC, position ASC")
    fun getAllOrder(): Flow<List<ChannelOrder>>

    @Query("SELECT * FROM channel_order WHERE groupId = :groupId ORDER BY position ASC")
    fun getOrderForGroup(groupId: String): Flow<List<ChannelOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOrder(orders: List<ChannelOrder>)

    @Query("DELETE FROM channel_order WHERE groupId = :groupId")
    suspend fun clearGroupOrder(groupId: String)

    @Query("DELETE FROM channel_order")
    suspend fun clearAll()
}
