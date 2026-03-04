package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val epgId: String?,
    val categoryId: String?
)
