package com.spendsms.app.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SpendSmsMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_transactions_amount_time
                ON transactions(amount_minor_units, currency, transaction_timestamp)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_corrections_field_transaction
                ON user_corrections(field_name, transaction_id)
                """.trimIndent(),
            )
        }
    }
}
