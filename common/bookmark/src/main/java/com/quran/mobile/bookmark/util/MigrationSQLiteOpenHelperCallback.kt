package com.quran.mobile.bookmark.util

import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

class AfterMigrationVersion(
  val afterVersion: Int,
  val block: (SupportSQLiteDatabase) -> Unit
)

/**
 * Hack until there's a fix for
 * https://github.com/cashapp/sqldelight/issues/2354 and/or
 * https://github.com/cashapp/sqldelight/issues/2614
 */
class MigrationSQLiteOpenHelperCallback(
  schema: SqlDriver.Schema,
  private vararg val callbacks: AfterMigrationVersion,
) : AndroidSqliteDriver.Callback(schema) {

  override fun onUpgrade(
    db: SupportSQLiteDatabase,
    oldVersion: Int,
    newVersion: Int
  ) {
    super.onUpgrade(db, oldVersion, newVersion)
    callbacks.filter { it.afterVersion in oldVersion until newVersion }
      .sortedBy { it.afterVersion }
      .forEach { callback ->
        callback.block(db)
      }
  }
}
