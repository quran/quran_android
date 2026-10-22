package com.quran.mobile.bookmark.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.quran.shared.persistence.MobileSyncDatabase
import com.quran.shared.persistence.MobileSyncSchema
import java.util.Properties

/**
 * A fresh in-memory mobile-sync database, opened the way the Android driver opens `user_data.db`:
 * [MobileSyncSchema] (which seeds Favorites) with foreign keys on.
 */
internal fun inMemoryMobileSyncDatabase(): MobileSyncDatabase {
  val driver = JdbcSqliteDriver(
    JdbcSqliteDriver.IN_MEMORY,
    Properties().apply { put("foreign_keys", "true") },
    MobileSyncSchema
  )
  return MobileSyncDatabase(driver)
}
