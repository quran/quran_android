package com.quran.labs.androidquran.database

import android.database.Cursor

object DatabaseUtils {
  @JvmStatic
  fun closeCursor(cursor: Cursor?) {
    try {
      cursor?.close()
    } catch (e: Exception) {
      // no op
    }
  }
}
