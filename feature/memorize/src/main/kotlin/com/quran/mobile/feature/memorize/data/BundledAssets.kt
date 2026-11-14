package com.quran.mobile.feature.memorize.data

import android.content.Context
import java.io.File
import java.io.IOException

/** Databases bundled as assets, which SQLite can only open once they are copied to a real file. */
internal object BundledAssets {

  /**
   * Copies [asset] to noBackupFilesDir/memorize (once per installed version of the app, so an
   * update that changes the asset replaces the old copy) and returns the copy.
   */
  @Throws(IOException::class)
  fun extract(context: Context, asset: String): File {
    val file = File(context.noBackupFilesDir, "memorize/${asset.substringAfterLast('/')}")
    val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    if (file.exists() && file.lastModified() >= installed) return file

    file.parentFile?.mkdirs()
    val partial = File(file.path + ".part")
    try {
      context.assets.open(asset).use { input -> partial.outputStream().use { input.copyTo(it) } }
      if (!partial.renameTo(file)) throw IOException("can't install $asset")
    } finally {
      partial.delete()
    }
    return file
  }
}
