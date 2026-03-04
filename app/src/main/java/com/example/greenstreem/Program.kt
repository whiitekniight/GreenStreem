package com.example.greenstreem

import java.io.Serializable
import java.util.Date

/**
 * Represents an EPG (Electronic Program Guide) entry.
 */
data class Program(
    val title: String,
    val description: String? = null,
    val startTime: Date,
    val endTime: Date,
    val channelId: String? = null
) : Serializable
