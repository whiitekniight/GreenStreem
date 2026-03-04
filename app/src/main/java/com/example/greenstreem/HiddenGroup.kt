package com.example.greenstreem

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_groups")
data class HiddenGroup(
    @PrimaryKey val groupId: String,
    val name: String
)
