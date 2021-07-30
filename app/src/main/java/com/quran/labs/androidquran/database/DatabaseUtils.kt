package com.quran.labs.androidquran.database

import android.database.Cursor
import java.lang.Exception

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
