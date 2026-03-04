package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_order")
data class ChannelOrder(
    @PrimaryKey val channelId: Long,
    val groupId: String,
    val position: Int
)
