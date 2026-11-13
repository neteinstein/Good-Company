package com.neteinstein.goodcompany.core.data.platform

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.neteinstein.goodcompany.core.data.db.DATABASE_FILE_NAME
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import org.koin.core.scope.Scope

/**
 * The system SQLite (`libsqlite3`). `ComposeApp.framework` is static, so the app target links it itself:
 * `-lsqlite3` in the iosApp target's `OTHER_LDFLAGS` (iosApp.xcodeproj).
 */
actual fun Scope.createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory { schema ->
    val driver = NativeSqliteDriver(
        schema = schema.synchronous(),
        name = DATABASE_FILE_NAME,
        onConfiguration = { config ->
            config.copy(extendedConfig = config.extendedConfig.copy(foreignKeyConstraints = true))
        },
    )
    OpenedDriver(driver, isPersistent = true)
}
