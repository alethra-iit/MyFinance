package com.myfinance.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "myfinance.db").build()
    }
}
