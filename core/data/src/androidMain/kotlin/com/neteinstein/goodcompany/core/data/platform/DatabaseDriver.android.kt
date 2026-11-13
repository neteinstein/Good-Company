package com.neteinstein.goodcompany.core.data.platform

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.neteinstein.goodcompany.core.data.db.DATABASE_FILE_NAME
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

/**
 * The database lives in the app's `databases/` directory: excluded from cloud backup, kept on device transfer
 * (androidApp `data_extraction_rules.xml`). The helper creates the schema on first open and runs `.sqm` migrations.
 */
actual fun Scope.createDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = androidContext()
    return DatabaseDriverFactory { schema ->
        val syncSchema = schema.synchronous()
        val driver = AndroidSqliteDriver(
            schema = syncSchema,
            context = context,
            name = DATABASE_FILE_NAME,
            callback = object : AndroidSqliteDriver.Callback(syncSchema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
        OpenedDriver(driver, isPersistent = true)
    }
}
