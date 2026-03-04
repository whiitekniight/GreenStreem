package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_order")
data class GroupOrder(
    @PrimaryKey val groupId: String,
    val position: Int
)
