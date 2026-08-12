package com.spendsms.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendsms.app.data.room.dao.CategoryDao
import com.spendsms.app.data.room.dao.DashboardCacheDao
import com.spendsms.app.data.room.dao.ParserMetadataDao
import com.spendsms.app.data.room.dao.ScanStateDao
import com.spendsms.app.data.room.dao.SubscriptionDao
import com.spendsms.app.data.room.dao.TransactionDao
import com.spendsms.app.data.room.dao.UserCorrectionDao
import com.spendsms.app.data.room.entity.CategoryEntity
import com.spendsms.app.data.room.entity.DashboardCacheEntity
import com.spendsms.app.data.room.entity.ParserMetadataEntity
import com.spendsms.app.data.room.entity.ScanStateEntity
import com.spendsms.app.data.room.entity.SubscriptionEntity
import com.spendsms.app.data.room.entity.SubscriptionTransactionCrossRef
import com.spendsms.app.data.room.entity.TransactionEntity
import com.spendsms.app.data.room.entity.UserCorrectionEntity
import com.spendsms.app.data.room.mapper.toEntity
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.SystemCategories
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Phase-0 on-device financial system of record (Step-3).
 *
 * Production builds use SQLCipher with a Keystore-backed passphrase and store
 * the file under [Context.getNoBackupFilesDir]. Raw SMS bodies are never stored.
 */
@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        UserCorrectionEntity::class,
        SubscriptionEntity::class,
        SubscriptionTransactionCrossRef::class,
        ScanStateEntity::class,
        ParserMetadataEntity::class,
        DashboardCacheEntity::class,
    ],
    version = SpendSmsDatabase.VERSION,
    exportSchema = true,
)
abstract class SpendSmsDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun userCorrectionDao(): UserCorrectionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun scanStateDao(): ScanStateDao
    abstract fun parserMetadataDao(): ParserMetadataDao
    abstract fun dashboardCacheDao(): DashboardCacheDao

    companion object {
        const val VERSION: Int = 1
        const val NAME: String = "spendsms.db"

        fun databaseFile(context: Context): File =
            File(context.applicationContext.noBackupFilesDir, NAME)

        /**
         * Encrypted production database under no-backup storage.
         *
         * @param passphrase SQLCipher key material from [com.spendsms.app.platform.security.DatabaseKeyProvider]
         */
        fun buildEncrypted(context: Context, passphrase: ByteArray): SpendSmsDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase.copyOf())
            return Room.databaseBuilder(
                context.applicationContext,
                SpendSmsDatabase::class.java,
                databaseFile(context).absolutePath,
            )
                .openHelperFactory(factory)
                .addCallback(SpendSmsDatabaseCallback())
                .build()
        }

        /** Unencrypted in-memory database for unit/Robolectric tests. */
        fun buildInMemory(context: Context): SpendSmsDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SpendSmsDatabase::class.java,
            )
                .allowMainThreadQueries()
                .addCallback(SpendSmsDatabaseCallback())
                .build()
    }
}

/**
 * Seeds system categories and creates the Step-3 partial future-rule index.
 */
class SpendSmsDatabaseCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_corrections_future_rule
            ON user_corrections(merchant_match_key, field_name)
            WHERE apply_to_future = 1
            """.trimIndent(),
        )
        seedSystemCategories(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys=ON")
    }

    private fun seedSystemCategories(db: SupportSQLiteDatabase) {
        val createdAt = System.currentTimeMillis()
        SystemCategories.seed(EpochMillis.of(createdAt)).forEach { category ->
            val entity = category.toEntity()
            db.execSQL(
                """
                INSERT OR IGNORE INTO categories(
                    category_id, name, is_system_category, sort_order, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    entity.categoryId,
                    entity.name,
                    if (entity.isSystemCategory) 1 else 0,
                    entity.sortOrder,
                    entity.createdAt,
                ),
            )
        }
    }
}
