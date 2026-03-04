package com.example.greenstreem

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupOrderDao {
    @Query("SELECT * FROM group_order ORDER BY position ASC")
    fun getAllOrder(): Flow<List<GroupOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOrder(orders: List<GroupOrder>)

    @Query("DELETE FROM group_order")
    suspend fun clearAllOrder()
}
