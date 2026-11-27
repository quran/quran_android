package com.quran.labs.androidquran.data

import android.content.Context
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

@ActivityScope
class AyahInfoDatabaseProvider @Inject constructor(
  @ApplicationContext private val context: Context,
  private val widthParameter: String,
  private val quranFileUtils: QuranFileUtils
) {
  @Volatile
  private var databaseHandler: AyahInfoDatabaseHandler? = null

  fun getAyahInfoHandler(): AyahInfoDatabaseHandler? {
    databaseHandler?.let { return it }
    return synchronized(this) {
      databaseHandler ?: run {
        val filename = quranFileUtils.getAyaPositionFileName(widthParameter)
        AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(
          filename,
          quranFileUtils
        ).also { databaseHandler = it }
      }
    }
  }

  fun getPageWidth(): Int = widthParameter.substring(1).toInt()
}
