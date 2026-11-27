package com.quran.labs.androidquran.data

import android.content.Context
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.memorization.QuranMemorizationManager
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.ZipUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@ActivityScope
class AyahInfoDatabaseProvider @Inject constructor(
  @ApplicationContext private val context: Context,
  private val widthParameter: String,
  private val quranFileUtils: QuranFileUtils
) {
  private var databaseHandler: AyahInfoDatabaseHandler? = null

  @Synchronized
  fun getAyahInfoHandler(): AyahInfoDatabaseHandler? {
    QuranMemorizationManager.ensureWordAlignmentLoaded(context)
    if (databaseHandler == null) {
      val filename = quranFileUtils.getAyaPositionFileName(widthParameter)
      ensureAyahInfoDatabaseExists(widthParameter, filename)
      databaseHandler = AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(
        filename,
        quranFileUtils
      )
      if (databaseHandler == null && widthParameter != "_1260") {
        val fallbackFilename = quranFileUtils.getAyaPositionFileName("_1260")
        ensureAyahInfoDatabaseExists("_1260", fallbackFilename)
        databaseHandler = AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(
          fallbackFilename,
          quranFileUtils
        )
      }
    }
    return databaseHandler
  }

  private fun ensureAyahInfoDatabaseExists(targetWidthParam: String, dbFilename: String) {
    val destDir = quranFileUtils.quranAyahDatabaseDirectory
    val dbFile = File(destDir, dbFilename)
    if (dbFile.exists() && dbFile.length() > 0L) {
      return
    }
    if (!destDir.exists()) {
      destDir.mkdirs()
    }

    val zipFilename = "ayahinfo$targetWidthParam.zip"
    val tempZip = File(destDir, zipFilename)

    // 1. Try extracting from bundled APK assets first (zero network latency)
    try {
      context.assets.open(zipFilename).use { input ->
        tempZip.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      if (tempZip.exists() && tempZip.length() > 0L) {
        ZipUtils.unzipFile(tempZip, destDir, zipFilename, null)
        tempZip.delete()
        if (dbFile.exists() && dbFile.length() > 0L) {
          return
        }
      }
    } catch (_: Exception) {
      tempZip.delete()
    }

    // 2. Otherwise download on-demand from android.quran.com
    try {
      val url = quranFileUtils.getAyaPositionFileUrl(targetWidthParam)
      val client = OkHttpClient()
      val request = Request.Builder().url(url).build()
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val body = response.body
          if (body != null) {
            body.byteStream().use { input ->
              tempZip.outputStream().use { output ->
                input.copyTo(output)
              }
            }
            if (tempZip.exists() && tempZip.length() > 0L) {
              ZipUtils.unzipFile(tempZip, destDir, zipFilename, null)
            }
          }
        }
      }
    } catch (_: Exception) {
      // Ignore; fallback will handle if offline
    } finally {
      if (tempZip.exists()) {
        tempZip.delete()
      }
    }
  }

  fun getPageWidth(): Int = widthParameter.substring(1).toInt()
}

