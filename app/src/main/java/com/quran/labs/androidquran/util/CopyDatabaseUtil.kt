package com.quran.labs.androidquran.util

import android.content.Context
import com.crashlytics.android.Crashlytics
import io.reactivex.Single
import okio.Okio
import java.io.File
import javax.inject.Inject

class CopyDatabaseUtil @Inject constructor(val context: Context,
                                           val quranFileUtils: QuranFileUtils) {

  fun copyArabicDatabaseFromAssets(name: String): Single<Boolean> {
    return Single.fromCallable {
      val assets = context.assets
      val files = assets.list("")
      val filename = files?.firstOrNull { it.contains(name) }
      val destination = quranFileUtils.getQuranDatabaseDirectory(context)
      if (filename != null && destination != null) {
        val destinationFile = File(destination)
        if (!destinationFile.isDirectory) {
          destinationFile.mkdirs()
        }

        // do the copy
        Okio.source(assets.open(filename)).use { source ->
          Okio.buffer(Okio.sink(File(destination, filename))).use { destination ->
            destination.writeAll(source)
          }
        }

        if (filename.endsWith(".zip")) {
          val zipFile = destination + File.separator + filename
          val result = ZipUtils.unzipFile(zipFile, destination, filename, null)
          // delete the zip file, since there's no need to have it twice
          File(zipFile).delete()
          result
        } else {
          true
        }
      } else {
        false
      }
    }
    .doOnError { Crashlytics.logException(it) }
    .onErrorReturn { false }
  }
}
