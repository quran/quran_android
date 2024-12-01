package com.quran.labs.androidquran.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.PNG
import android.graphics.Bitmap.Config.ALPHA_8
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.os.Environment
import androidx.annotation.WorkerThread
import com.quran.data.core.QuranFileManager
import com.quran.data.model.audio.Qari
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.extension.closeQuietly
import com.quran.mobile.di.qualifier.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.text.NumberFormat
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

class QuranFileUtils @Inject constructor(
  @ApplicationContext context: Context,
  pageProvider: PageProvider,
  private val quranScreenInfo: QuranScreenInfo
): QuranFileManager {
  // server urls
  private val imageBaseUrl: String = pageProvider.getImagesBaseUrl()
  private val imageZipBaseUrl: String = pageProvider.getImagesZipBaseUrl()
  private val patchBaseUrl: String = pageProvider.getPatchBaseUrl()
  private val databaseBaseUrl: String = pageProvider.getDatabasesBaseUrl()
  private val ayahInfoBaseUrl: String = pageProvider.getAyahInfoBaseUrl()

  // local paths
  private val databaseDirectory: String = pageProvider.getDatabaseDirectoryName()
  private val audioDirectory: String = pageProvider.getAudioDirectoryName()
  private val ayahInfoDirectory: String = pageProvider.getAyahInfoDirectoryName()
  private val imagesDirectory: String = pageProvider.getImagesDirectoryName()

  val ayahInfoDbHasGlyphData = pageProvider.ayahInfoDbHasGlyphData()

  private val appContext: Context = context.applicationContext
  val gaplessDatabaseRootUrl: String = pageProvider.getAudioDatabasesBaseUrl()
  private val recitationPath = "recitation"

  // check if the images with the given width param have a version
  // that we specify (ex if version is 3, check for a .v3 file).
  @WorkerThread
  override fun isVersion(widthParam: String, version: Int): Boolean {
    // version 1 or below are true as long as you have images
    return version <= 1 || hasVersionFile(widthParam, version)
  }

  private fun hasVersionFile(widthParam: String, version: Int): Boolean {
    val quranDirectory = getQuranImagesDirectory(widthParam)
    Timber.d(
        "isVersion: checking if version %d exists for width %s at %s",
        version, widthParam, quranDirectory
    )
    return try {
      File(quranDirectory, ".v$version").exists()
    } catch (e: Exception) {
      Timber.e(e, "isVersion: exception while checking version file")
      false
    }

    // check the version code
  }

  fun getPotentialFallbackDirectory(totalPages: Int): String? {
    val state = Environment.getExternalStorageState()
    if (state == Environment.MEDIA_MOUNTED) {
      if (haveAllImages("_1920", totalPages, false)) {
        return "1920"
      }
    }
    return null
  }

  override fun quranImagesDirectory(): File = getQuranImagesDirectory()
  override fun ayahInfoFileDirectory(): File {
    val base = quranAyahDatabaseDirectory
    return File(base, ayaPositionFileName)
  }

  @WorkerThread
  override fun removeFilesForWidth(width: Int, directoryLambda: ((String) -> String)) {
    val widthParam = "_$width"
    val quranDirectoryWithoutLambda = getQuranImagesDirectory(widthParam)
    val quranDirectory = directoryLambda(quranDirectoryWithoutLambda.absolutePath)
    val file = File(quranDirectory)
    if (file.exists()) {
      deleteFileOrDirectory(file)
      val ayahDatabaseDirectoryWithoutLambda = quranAyahDatabaseDirectory
      val ayahDatabaseDirectory = directoryLambda(ayahDatabaseDirectoryWithoutLambda.absolutePath)
      val ayahinfoFile = File(ayahDatabaseDirectory, "ayahinfo_$width.db")
      if (ayahinfoFile.exists()) {
        ayahinfoFile.delete()
      }
    }
  }

  @WorkerThread
  override fun writeVersionFile(widthParam: String, version: Int) {
    val quranDirectory = getQuranImagesDirectory(widthParam) ?: return
    File(quranDirectory, ".v$version").createNewFile()
  }

  @WorkerThread
  override fun writeNoMediaFileRelative(widthParam: String) {
    val quranDirectory = getQuranImagesDirectory(widthParam)
    writeNoMediaFile(quranDirectory)
  }

  fun haveAllImages(
    widthParam: String,
    totalPages: Int,
    makeDirectory: Boolean,
  ): Boolean {
    val quranDirectory = getQuranImagesDirectory(widthParam)
    Timber.d("haveAllImages: for width %s, directory is: %s", widthParam, quranDirectory)
    if (quranDirectory.isDirectory) {
      Timber.d("haveAllImages: media state is mounted and directory exists")
      val fileList = quranDirectory.list()
      if (fileList == null) {
        Timber.d("haveAllImages: null fileList, checking page by page...")
        for (i in 1..totalPages) {
          val name = getPageFileName(i)
          if (!File(quranDirectory, name).exists() && !File(quranDirectory, i.toString()).exists()) {
            Timber.d("haveAllImages: couldn't find page %d", i)
            return false
          }
        }
      } else if (fileList.size < totalPages) {
        // ideally, we should loop for each page and ensure
        // all pages are there, but this will do for now.
        Timber.d("haveAllImages: found %d files instead of 604.", fileList.size)
        return false
      }
      return true
    } else {
      Timber.d(
        "haveAllImages: couldn't find the directory, so %s",
        if (makeDirectory) "making it instead." else "doing nothing."
      )
      if (makeDirectory) {
        makeQuranImagesDirectory(widthParam)
      }
    }
    return false
  }

  private val isSDCardMounted: Boolean
    get() {
      val state = Environment.getExternalStorageState()
      return state == Environment.MEDIA_MOUNTED
    }

  fun getImageFromSD(
    widthParam: String?,
    filename: String
  ): Response {
    val location: File =
      widthParam?.let { getQuranImagesDirectory(it) }
          ?: getQuranImagesDirectory()
    val options = Options()
    options.inPreferredConfig = ALPHA_8
    val bitmap = BitmapFactory.decodeFile(File(location, filename).absolutePath, options)
    return bitmap?.let { Response(it) } ?: Response(Response.ERROR_FILE_NOT_FOUND)
  }

  private fun writeNoMediaFile(parentDir: File): Boolean {
    val f = File(parentDir, ".nomedia")
    return if (f.exists()) {
      true
    } else try {
      f.createNewFile()
    } catch (e: IOException) {
      false
    }
  }

  fun makeQuranImagesDirectory(widthParam: String): Boolean {
    val directory = getQuranImagesDirectory(widthParam)
    return if (directory.exists() && directory.isDirectory) {
      writeNoMediaFile(directory)
    } else {
      directory.mkdirs() && writeNoMediaFile(directory)
    }
  }

  private fun makeDirectory(path: String?): Boolean {
    if (path == null) { return false }
    val directory = File(path)
    return makeDirectory(directory)
  }

  private fun makeDirectory(directory: File): Boolean {
    return directory.exists() && directory.isDirectory || directory.mkdirs()
  }

  @WorkerThread
  override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String) {
    val actualDestination = File(quranInternalBaseDirectory, destination)
    if (!actualDestination.exists()) {
      if (actualDestination.absolutePath.endsWith(filename)) {
        actualDestination.parentFile?.mkdirs()
      } else {
        actualDestination.mkdirs()
      }
    }
    copyFromAssets(assetsPath, filename, actualDestination)
  }

  override fun copyFromAssetsRelativeRecursive(
    assetsPath: String,
    directory: String,
    destination: String
  ) {
    val destinationPath = File(quranInternalBaseDirectory, destination)
    val directoryDestinationPath = File(destinationPath, directory)
    if (!directoryDestinationPath.exists()) {
      directoryDestinationPath.mkdirs()
    }

    val assets = appContext.assets
    val files = assets.list(assetsPath) ?: emptyArray()
    val destinationDirectory = "$destination${File.separator}$directory"
    files.forEach {
      val path = "$assetsPath${File.separator}$it"
      if (assets.list(path)?.isNotEmpty() == true) {
        copyFromAssetsRelativeRecursive(path, it, destinationDirectory)
      } else {
        copyFromAssetsRelative(path, it, destinationDirectory)
      }
    }
  }

  @WorkerThread
  override fun removeOldArabicDatabase(): Boolean {
    val databaseQuranArabicDatabase = File(
      getQuranDatabaseDirectory(),
      QuranDataProvider.QURAN_ARABIC_DATABASE
    )

    return if (databaseQuranArabicDatabase.exists()) {
      databaseQuranArabicDatabase.delete()
    } else true
  }

  @WorkerThread
  private fun copyFromAssets(assetsPath: String, filename: String, destination: File) {
    val assets = appContext.assets
    assets.open(assetsPath)
        .source()
        .use { source ->
          File(destination, filename).sink()
              .buffer()
              .use { destination -> destination.writeAll(source) }
        }

    if (filename.endsWith(".zip")) {
      val zipFile = File(destination, filename)
      ZipUtils.unzipFile(zipFile, destination, filename, null)
      // delete the zip file, since there's no need to have it twice
      zipFile.delete()
    }
  }

  fun getImageFromWeb(
    okHttpClient: OkHttpClient,
    context: Context,
    widthParam: String,
    filename: String
  ): Response {
    return getImageFromWeb(okHttpClient, context, widthParam, filename, false)
  }

  private fun getImageFromWeb(
    okHttpClient: OkHttpClient,
    context: Context,
    widthParam: String,
    filename: String,
    isRetry: Boolean
  ): Response {
    val base = imageBaseUrl
    val urlString = (base + "width" + widthParam + File.separator + filename)
    Timber.d("want to download: %s", urlString)
    val request = Builder()
        .url(urlString)
        .build()
    val call = okHttpClient.newCall(request)
    var responseBody: ResponseBody? = null
    try {
      val response = call.execute()
      if (response.isSuccessful) {
        responseBody = response.body
        if (responseBody != null) {
          // handling for BitmapFactory.decodeStream not throwing an error
          // when the download is interrupted or an exception occurs. This
          // is taken from both Glide (see ExceptionHandlingInputStream) and
          // Picasso (see BitmapFactory).
          val exceptionCatchingSource =
            ExceptionCatchingSource(responseBody.source())
          val bufferedSource = exceptionCatchingSource.buffer()
          val bitmap = decodeBitmapStream(bufferedSource.inputStream())
          // throw if an error occurred while decoding the stream
          exceptionCatchingSource.throwIfCaught()
          if (bitmap != null) {
            val path = getQuranImagesDirectory(widthParam)
            var warning = Response.WARN_SD_CARD_NOT_FOUND
            if (makeQuranImagesDirectory(widthParam)) {
              val resultPath = File(path, filename)
              warning = if (tryToSaveBitmap(
                      bitmap, resultPath
                  )
              ) 0 else Response.WARN_COULD_NOT_SAVE_FILE
            }
            return Response(bitmap, warning)
          }
        }
      }
    } catch (iioe: InterruptedIOException) {
      // do nothing, this is expected if the job is canceled
    } catch (ioe: IOException) {
      Timber.e(ioe, "exception downloading file")
    } finally {
      responseBody.closeQuietly()
    }
    return if (isRetry) Response(
        Response.ERROR_DOWNLOADING_ERROR
    ) else getImageFromWeb(okHttpClient, context, widthParam, filename, true)
  }

  private fun decodeBitmapStream(`is`: InputStream): Bitmap? {
    val options = Options()
    options.inPreferredConfig = ALPHA_8
    return BitmapFactory.decodeStream(`is`, null, options)
  }

  private fun tryToSaveBitmap(bitmap: Bitmap, savePath: File): Boolean {
    var output: FileOutputStream? = null
    try {
      output = FileOutputStream(savePath)
      return bitmap.compress(PNG, 100, output)
    } catch (ioe: IOException) {
      // do nothing
    } finally {
      try {
        if (output != null) {
          output.flush()
          output.close()
        }
      } catch (e: Exception) {
        // ignore...
      }
    }
    return false
  }

  private val quranInternalBaseDirectory: File
    get() = File(appContext.filesDir, QURAN_BASE)

  val quranBaseDirectory: String?
    get() = getQuranBaseDirectory(appContext)

  val quranInternalStorage: File
    get() = quranInternalBaseDirectory

  fun getQuranBaseDirectory(context: Context): String? {
    var basePath = QuranSettings.getInstance(context).appCustomLocation
    if (!isSDCardMounted) {
      // if our best guess suggests that we won't have access to the data due to the sdcard not
      // being mounted, then set the base path to null for now.
      if (basePath == null || basePath == Environment.getExternalStorageDirectory().absolutePath ||
          basePath.contains(BuildConfig.APPLICATION_ID) &&
          context.getExternalFilesDir(null) == null) {
        basePath = null
      }
    }

    if (basePath != null) {
      if (!basePath.endsWith(File.separator)) {
        basePath += File.separator
      }
      return basePath + QURAN_BASE
    }
    return null
  }

  /**
   * Returns the app used space in megabytes
   */
  fun getAppUsedSpace(context: Context): Int {
    val baseDirectory = getQuranBaseDirectory(context) ?: return -1
    val base = File(baseDirectory)
    val files = ArrayList<File>()
    files.add(base)
    var size: Long = 0
    while (files.isNotEmpty()) {
      val f = files.removeAt(0)
      if (f.isDirectory) {
        val subFiles = f.listFiles()
        if (subFiles != null) {
          Collections.addAll(files, *subFiles)
        }
      } else {
        size += f.length()
      }
    }
    return (size / (1024 * 1024).toLong()).toInt()
  }

  fun getQuranDatabaseDirectory(): File {
    val base = quranInternalBaseDirectory
    return File(base, databaseDirectory)
  }

  val quranAyahDatabaseDirectory: File
    get() = File(quranInternalBaseDirectory, ayahInfoDirectory)

  override fun audioFileDirectory(): String? = getQuranAudioDirectory(appContext)

  fun getQuranAudioDirectory(context: Context): String? {
    val path = getQuranBaseDirectory(context)?.let { it + audioDirectory } ?: return null
    val dir = File(path)
    if (!dir.exists() && !dir.mkdirs()) {
      return null
    }
    writeNoMediaFile(dir)
    return path + File.separator
  }

  override fun urlForDatabase(qari: Qari): String {
    return gaplessDatabaseRootUrl + qari.databaseName + AudioUtils.ZIP_EXTENSION
  }

  fun getQuranImagesBaseDirectory(): File {
    return File(quranInternalBaseDirectory, imagesDirectory)
  }

  private fun getQuranImagesDirectory(): File {
    return getQuranImagesDirectory(quranScreenInfo.widthParam)
  }

  fun getQuranImagesDirectory(
    widthParam: String
  ): File {
    val base = getQuranImagesBaseDirectory()
    return File(base, "width$widthParam")
  }

  private fun recitationsDirectory(): String {
    val recitationDirectory = getQuranBaseDirectory(appContext).toString() + "$recitationPath/"
    makeDirectory(recitationDirectory)
    return recitationDirectory
  }

  override fun recitationSessionsDirectory(): String {
    val sessionsDirectory = recitationsDirectory() + "sessions/"
    makeDirectory(sessionsDirectory)
    return sessionsDirectory
  }

  override fun recitationRecordingsDirectory(): String {
    val recordingsDirectory = recitationsDirectory() + "recordings/"
    makeDirectory(recordingsDirectory)
    return recordingsDirectory
  }

  val zipFileUrl: String
    get() = getZipFileUrl(quranScreenInfo.widthParam)

  fun getZipFileUrl(widthParam: String): String {
    var url = imageZipBaseUrl
    url += "images$widthParam.zip"
    return url
  }

  fun getPatchFileUrl(
    widthParam: String,
    toVersion: Int
  ): String {
    return patchBaseUrl + toVersion + "/patch" +
        widthParam + "_v" + toVersion + ".zip"
  }

  private val ayaPositionFileName: String
    get() = getAyaPositionFileName(quranScreenInfo.widthParam)

  fun getAyaPositionFileName(widthParam: String): String {
    return "ayahinfo$widthParam.db"
  }

  val ayaPositionFileUrl: String
    get() = getAyaPositionFileUrl(quranScreenInfo.widthParam)

  fun getAyaPositionFileUrl(widthParam: String): String {
    return ayahInfoBaseUrl + "ayahinfo" + widthParam + ".zip"
  }

  fun haveAyaPositionFile(): Boolean {
    val base = quranAyahDatabaseDirectory
    val filename = ayaPositionFileName
    return File(base, filename).exists()
  }

  fun hasTranslation(fileName: String): Boolean {
    val path = getQuranDatabaseDirectory()
    return File(path, fileName)
      .exists()
  }

  @WorkerThread
  override fun hasArabicSearchDatabase(): Boolean {
    if (hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE)) {
      return true
    } else if (databaseDirectory != ayahInfoDirectory) {
      // non-hafs flavors copy their ayahinfo and arabic search database in a subdirectory,
      // so we copy back the arabic database into the translations directory where it can
      // be shared across all flavors of quran android
      val ayahInfoFile = File(
          quranAyahDatabaseDirectory,
          QuranDataProvider.QURAN_ARABIC_DATABASE
      )
      val base = getQuranDatabaseDirectory()
      if (ayahInfoFile.exists()) {
        val translationsFile =
          File(base, QuranDataProvider.QURAN_ARABIC_DATABASE)
        if (base.exists() || base.mkdirs() == true) {
          try {
            copyFile(ayahInfoFile, translationsFile)
            return true
          } catch (ioe: IOException) {
            if (!translationsFile.delete()) {
              Timber.e("Error deleting translations file")
            }
          }
        }
      }
    }
    return false
  }

  val arabicSearchDatabaseUrl: String
    get() = databaseBaseUrl + QuranDataProvider.QURAN_ARABIC_DATABASE + ".zip"

  fun moveAppFiles(context: Context, newLocation: String): Boolean {
    if (QuranSettings.getInstance(context).appCustomLocation == newLocation) {
      return true
    }
    val baseDir = getQuranBaseDirectory(context) ?: return false
    val currentDirectory = File(baseDir)
    val newDirectory = File(newLocation, QURAN_BASE)
    if (!currentDirectory.exists()) {
      // No files to copy, so change the app directory directly
      return true
    } else if (newDirectory.exists() || newDirectory.mkdirs()) {
      try {
        // if we're copying to filesDir, copy everything - theoretically, due to upgrade
        // happening first, this should only be audio files, but in rare cases (ex upgrading
        // from a very old app version), it could actually be everything.
        if (newLocation == appContext.filesDir.absolutePath) {
          copyFileOrDirectory(currentDirectory, newDirectory)
          try {
            Timber.d("Removing $currentDirectory due to move to $newDirectory")
            deleteFileOrDirectory(currentDirectory)
          } catch (e: IOException) {
            // swallow silently
            Timber.e(e, "warning while deleting app files")
          }
          return true
        } else {
          // if we're copying to a custom location, only copy the audio files (and recitations)
          val directories = listOf(audioDirectory, recitationPath)
          for (dir in directories) {
            val currentDir = File(currentDirectory, dir)
            val newDir = File(newDirectory, dir)
            if (currentDir.exists()) {
              copyFileOrDirectory(currentDir, newDir)
              try {
                deleteFileOrDirectory(currentDir)
              } catch (e: IOException) {
                // swallow silently
                Timber.e(e, "warning while deleting app files")
              }
            }
          }
          return true
        }
      } catch (e: IOException) {
        Timber.e(e, "error moving app files")
      }
    }
    return false
  }

  override fun upgradeNonAudioFiles(portraitWidth: String, landscapeWidth: String, totalPages: Int): Boolean {
    val baseDirectory = quranBaseDirectory
    val quranFilesDir = baseDirectory?.let { File(baseDirectory).canonicalPath }
    val internalFilesDir = appContext.filesDir.canonicalPath

    // only do the copy if the base directory is not null, and the base directory is
    // not already the same as the internal directory.
    if (baseDirectory != null && quranFilesDir?.startsWith(internalFilesDir + File.separator) == false) {
      val directories = if (imagesDirectory.isEmpty()) {
        mapOf(
          "width" + quranScreenInfo.widthParam to getQuranImagesDirectory(),
          databaseDirectory to getQuranDatabaseDirectory(),
          ayahInfoDirectory to quranAyahDatabaseDirectory
        )
      } else {
        mapOf(
          imagesDirectory to getQuranImagesBaseDirectory(),
          databaseDirectory to getQuranDatabaseDirectory()
        )
      }

      for ((dirName, dir) in directories) {
        val oldDir = File(baseDirectory, dirName)
        if (oldDir.exists()) {
          try {
            copyFileOrDirectory(oldDir, dir)
            deleteFileOrDirectory(oldDir)
          } catch (e: IOException) {
            Timber.e(e, "error upgrading non-audio files")
          }
        }
      }
    }
    return haveAllImages(portraitWidth, totalPages, true) &&
        (portraitWidth == landscapeWidth || haveAllImages(landscapeWidth, totalPages, true))
  }

  private fun deleteFileOrDirectory(file: File) {
    if (file.isDirectory) {
      val subFiles = file.listFiles()
      // subFiles is null on some devices, despite this being a directory
      val length = subFiles?.size ?: 0
      for (i in 0 until length) {
        val sf = subFiles!![i]
        if (sf.isFile) {
          if (!sf.delete()) {
            Timber.e("Error deleting %s", sf.path)
          }
        } else {
          deleteFileOrDirectory(sf)
        }
      }
    }
    if (!file.delete()) {
      Timber.e("Error deleting %s", file.path)
    }
  }

  private fun copyFileOrDirectory(source: File, destination: File) {
    if (source.isDirectory) {
      if (!destination.exists() && !destination.mkdirs()) {
        return
      }

      val files = source.listFiles() ?: throw IOException("null listFiles() output...")
      for (f in files) {
        copyFileOrDirectory(f, File(destination, f.name))
      }
    } else {
      copyFile(source, destination)
    }
  }

  private fun copyFile(source: File, destination: File) {
    destination.sink().buffer().use { sink ->
      source.source().use { source -> sink.writeAll(source) }
    }
  }

  // taken from Picasso's BitmapUtils class
  // also Glide's ExceptionHandlingInputSource
  internal class ExceptionCatchingSource(delegate: Source) : ForwardingSource(delegate) {
    var ioException: IOException? = null
    @Throws(IOException::class)
    override fun read(sink: Buffer, byteCount: Long): Long {
      return try {
        super.read(sink, byteCount)
      } catch (ioe: IOException) {
        ioException = ioe
        throw ioException!!
      }
    }

    @Throws(IOException::class)
    fun throwIfCaught() {
      if (ioException != null) {
        throw ioException as IOException
      }
    }
  }

  companion object {
    private const val QURAN_BASE = "quran_android/"
    @JvmStatic fun getPageFileName(p: Int): String {
      val nf = NumberFormat.getInstance(Locale.US)
      nf.minimumIntegerDigits = 3
      return "page" + nf.format(p.toLong()) + ".png"
    }
  }
}
