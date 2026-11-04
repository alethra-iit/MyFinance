package com.myfinance.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        UserSettingsEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        DebtEntity::class,
        ReceiptEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun settingsDao(): SettingsDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun debtDao(): DebtDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        /** v1 -> v2: tambah tabel anggaran & transaksi berulang. Data lama tidak disentuh. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`category`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`frequency` TEXT NOT NULL, `anchorDay` INTEGER NOT NULL, `nextDue` INTEGER NOT NULL, `active` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 -> v3: tambah tabel utang/piutang. Data lama tidak disentuh. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`person` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `paid` INTEGER NOT NULL, " +
                        "`dueDate` INTEGER, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v3 -> v4: ikon & warna kustom per kategori (kosong/0 = tampilan bawaan, jadi data lama tetap sama). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `icon` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 -> v5: tabel lampiran foto struk (Base64). Data lama tidak disentuh. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `receipts` (`transactionId` INTEGER NOT NULL, `data` TEXT NOT NULL, " +
                        "PRIMARY KEY(`transactionId`), " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        fun build(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "myfinance.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
