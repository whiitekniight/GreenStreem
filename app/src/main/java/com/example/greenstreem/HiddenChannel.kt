package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_channels")
data class HiddenChannel(
    @PrimaryKey val channelId: Long,
    val name: String
)
