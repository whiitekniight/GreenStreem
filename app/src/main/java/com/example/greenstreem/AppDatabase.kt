package com.example.greenstreem

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Favorite::class, HiddenGroup::class, ChannelOrder::class, GroupOrder::class, HiddenChannel::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun groupDao(): GroupDao
    abstract fun channelOrderDao(): ChannelOrderDao
    abstract fun groupOrderDao(): GroupOrderDao
    abstract fun hiddenChannelDao(): HiddenChannelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
