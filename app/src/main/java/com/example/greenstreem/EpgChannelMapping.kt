package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_channel_mappings")
data class EpgChannelMapping(
    @PrimaryKey val channelId: Long,
    val provider: String,
    val epgChannelId: String,
    val displayName: String
)
