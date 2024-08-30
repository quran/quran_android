package com.quran.labs.androidquran.service

import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Parcelable
import android.os.StatFs
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.extension.closeQuietly
import com.quran.labs.androidquran.service.download.DownloadStrategy
import com.quran.labs.androidquran.service.download.GaplessDownloadStrategy
import com.quran.labs.androidquran.service.download.GappedDownloadStrategy
import com.quran.labs.androidquran.service.download.SingleFileDownloadStrategy
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent
import com.quran.labs.androidquran.service.util.QuranDownloadNotifierImpl
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.UrlUtil
import com.quran.labs.androidquran.util.ZipUtils
import com.quran.labs.androidquran.util.ZipUtils.ZipListener
import com.quran.mobile.common.download.DownloadInfoStreams
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import okio.appendingSink
import okio.buffer
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.concurrent.Volatile

class QuranDownloadService : Service(), ZipListener<NotificationDetails> {
  private lateinit var serviceLooper: Looper
  private lateinit var serviceHandler: ServiceHandler
  private lateinit var notifier: QuranDownloadNotifier

  // written from ui thread and read by download thread
  @Volatile
  private var isDownloadCanceled = false
  private lateinit var broadcastManager: LocalBroadcastManager
  private lateinit var quranSettings: QuranSettings
  private lateinit var wifiLock: WifiLock

  private var lastSentIntent: Intent? = null
  private val successfulZippedDownloads: MutableMap<String, Boolean> = mutableMapOf()
  private val recentlyFailedDownloads: MutableMap<String, Intent> = mutableMapOf()

  // incremented from ui thread and decremented by download thread
  private val currentOperations = AtomicInteger(0)

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var okHttpClient: OkHttpClient

  @Inject
  lateinit var urlUtil: UrlUtil

  @Inject
  lateinit var downloadInfoStreams: DownloadInfoStreams

  private inner class ServiceHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      if (msg.obj != null) {
        onHandleIntent(msg.obj as Intent)
        if (0 == currentOperations.decrementAndGet()) {
          notifier.stopForeground()
        }
      }
      stopSelf(msg.arg1)
    }
  }

  override fun onCreate() {
    super.onCreate()
    val thread = HandlerThread(TAG)
    thread.start()

    val appContext = applicationContext
    wifiLock = (appContext.getSystemService(WIFI_SERVICE) as WifiManager)
      .createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadLock")

    serviceLooper = thread.looper
    serviceHandler = ServiceHandler(serviceLooper)
    isDownloadCanceled = false
    quranSettings = QuranSettings.getInstance(this)

    (application as QuranApplication).applicationComponent.inject(this)
    broadcastManager = LocalBroadcastManager.getInstance(appContext)
    notifier = QuranDownloadNotifierImpl(this, this, downloadInfoStreams)
  }

  private fun handleOnStartCommand(intent: Intent?, startId: Int) {
    if (intent != null) {
      if (ACTION_CANCEL_DOWNLOADS == intent.action) {
        serviceHandler.removeCallbacksAndMessages(null)
        isDownloadCanceled = true
        sendNoOpMessage(startId)
      } else if (ACTION_RECONNECT == intent.action) {
        val type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, DOWNLOAD_TYPE_UNDEF)
        val currentLast = lastSentIntent
        val lastType = currentLast?.getIntExtra(
          EXTRA_DOWNLOAD_TYPE,
          DOWNLOAD_TYPE_UNDEF
        ) ?: -1

        if (type == lastType) {
          if (currentLast != null) {
            broadcastManager.sendBroadcast(currentLast)
          }
        } else if (serviceHandler.hasMessages(type)) {
          val progressIntent = Intent(ProgressIntent.INTENT_NAME)
          progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, type)
          progressIntent.putExtra(
            ProgressIntent.STATE,
            ProgressIntent.STATE_DOWNLOADING
          )
          broadcastManager.sendBroadcast(progressIntent)
        }
        sendNoOpMessage(startId)
      } else {
        // if we are currently downloading, resend the last broadcast
        // and don't queue anything
        val download = intent.getStringExtra(EXTRA_DOWNLOAD_KEY)
        val currentLast = lastSentIntent
        val currentDownload = currentLast?.getStringExtra(ProgressIntent.DOWNLOAD_KEY)
        if (download != null && download == currentDownload) {
          Timber.d("resending last broadcast...")
          broadcastManager.sendBroadcast(currentLast)

          val state = currentLast.getStringExtra(ProgressIntent.STATE)
          if (ProgressIntent.STATE_SUCCESS != state && ProgressIntent.STATE_ERROR != state) {
            // re-queue fatal errors and success cases again just in case
            // of a race condition in which we miss the error pref and
            // miss the success/failure notification and this re-play
            sendNoOpMessage(startId)
            Timber.d("leaving...")
            return
          }
        }

        val what = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, DOWNLOAD_TYPE_UNDEF)
        currentOperations.incrementAndGet()
        // put the message in the queue
        val msg = serviceHandler.obtainMessage()
        msg.arg1 = startId
        msg.obj = intent
        msg.what = what
        serviceHandler.sendMessage(msg)
      }
    }
  }

  /**
   * send a no-op message to the handler to ensure
   * that the service isn't left running.
   *
   * @param id the start id
   */
  private fun sendNoOpMessage(id: Int) {
    val msg = serviceHandler.obtainMessage()
    msg.arg1 = id
    msg.obj = null
    msg.what = NO_OP
    serviceHandler.sendMessage(msg)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent != null) {
      // if it's a download, it wants to be a foreground service.
      // quickly start as foreground before actually enqueueing the request.
      if (ACTION_DOWNLOAD_URL == intent.action) {
        notifier.notifyDownloadStarting()
      }

      handleOnStartCommand(intent, startId)
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    if (wifiLock.isHeld) {
      wifiLock.release()
    }
    serviceLooper.quit()
  }

  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  private fun onHandleIntent(intent: Intent) {
    if (ACTION_DOWNLOAD_URL == intent.action) {
      val url = intent.getStringExtra(EXTRA_URL)
      val key = intent.getStringExtra(EXTRA_DOWNLOAD_KEY)
      val type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, 0)
      val notificationTitle =
        intent.getStringExtra(EXTRA_NOTIFICATION_NAME)
      val metadata = intent.getParcelableExtra<Parcelable>(EXTRA_METADATA)

      val details =
        NotificationDetails(notificationTitle, key, type, metadata)
      // check if already downloaded, and if so, send broadcast
      val isZipFile = url!!.endsWith(".zip")
      if (isZipFile && successfulZippedDownloads.containsKey(url)) {
        lastSentIntent = notifier.broadcastDownloadSuccessful(details)
        return
      } else if (recentlyFailedDownloads.containsKey(url)) {
        // if recently failed and we want to repeat the last error...
        if (intent.getBooleanExtra(EXTRA_REPEAT_LAST_ERROR, false)) {
          val failedIntent = recentlyFailedDownloads[url]
          if (failedIntent != null) {
            // re-broadcast and leave - just in case of race condition
            broadcastManager.sendBroadcast(failedIntent)
            return
          }
        } else {
          recentlyFailedDownloads.remove(url)
        }
      }
      notifier.resetNotifications()

      // get the start/end ayah info if it's a ranged download
      val startAyah = intent.getSerializableExtra(EXTRA_START_VERSE) as SuraAyah?
      val endAyah = intent.getSerializableExtra(EXTRA_END_VERSE) as SuraAyah?
      val isGapless = intent.getBooleanExtra(EXTRA_IS_GAPLESS, false)

      var outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME)
      if (outputFile == null) {
        outputFile = getFilenameFromUrl(url)
      }
      val destination = intent.getStringExtra(EXTRA_DESTINATION)
      lastSentIntent = null

      if (destination == null) {
        return
      }

      val batch = intent.getIntArrayExtra(EXTRA_AUDIO_BATCH)

      val strategy: DownloadStrategy
      if (startAyah != null && endAyah != null) {
        val databaseUrl = intent.getStringExtra(EXTRA_DOWNLOAD_DATABASE)
        strategy = if (isGapless) {
          GaplessDownloadStrategy(
            startAyah, endAyah,
            quranInfo,
            url, destination,
            notifier, details, databaseUrl
          ) { urlString: String, destination: String, outputFile: String, details: NotificationDetails ->
            this.downloadFileWrapper(
              urlString,
              destination,
              outputFile,
              details
            )
          }
        } else {
          GappedDownloadStrategy(
            startAyah, endAyah,
            url,
            quranInfo, destination,
            notifier, details
          ) { urlString: String, destination: String, outputFile: String, details: NotificationDetails ->
            this.downloadFileWrapper(
              urlString,
              destination,
              outputFile,
              details
            )
          }
        }
      } else if (batch != null) {
        val databaseUrl = intent.getStringExtra(EXTRA_DOWNLOAD_DATABASE)
        val ranges = verseRangesFromSuraList(batch)
        strategy = if (isGapless) {
          GaplessDownloadStrategy(
            ranges,
            url, destination,
            notifier, details, databaseUrl
          ) { urlString: String, destination: String, outputFile: String, details: NotificationDetails ->
            this.downloadFileWrapper(
              urlString,
              destination,
              outputFile,
              details
            )
          }
        } else {
          GappedDownloadStrategy(
            ranges,
            url,
            quranInfo, destination,
            notifier, details
          ) { urlString: String, destination: String, outputFile: String, details: NotificationDetails ->
            this.downloadFileWrapper(
              urlString,
              destination,
              outputFile,
              details
            )
          }
        }
      } else {
        strategy = SingleFileDownloadStrategy(
          url, destination, outputFile, details,
          notifier
        ) { urlString: String, destination: String, outputFile: String, details: NotificationDetails ->
          this.downloadFileWrapper(
            urlString,
            destination,
            outputFile,
            details
          )
        }
      }

      details.setIsGapless(isGapless)
      val result = downloadCommon(strategy, details)

      if (result && isZipFile) {
        successfulZippedDownloads[url] = true
      } else if (!result) {
        val lastIntent = lastSentIntent
        if (lastIntent != null) {
          recentlyFailedDownloads[url] = lastIntent
        }
      }
      lastSentIntent = null
    }
  }

  private fun verseRangesFromSuraList(suras: IntArray): List<VerseRange> {
    val results: MutableList<VerseRange> = ArrayList()
    for (sura in suras) {
      val ayahCount = quranInfo.getNumberOfAyahs(sura)
      results.add(VerseRange(sura, 1, sura, ayahCount, ayahCount))
    }
    return results
  }

  private fun downloadCommon(strategy: DownloadStrategy, details: NotificationDetails): Boolean {
    details.setFileStatus(1, strategy.fileCount())
    lastSentIntent = notifier.notifyProgress(details, 0, 0)

    if (strategy.downloadFiles()) {
      lastSentIntent = notifier.notifyDownloadSuccessful(details)
      return true
    } else {
      return false
    }
  }

  private fun downloadFileWrapper(
    urlString: String, destination: String,
    outputFile: String, details: NotificationDetails
  ): Boolean {
    var previouslyCorrupted = false

    var res = DOWNLOAD_SUCCESS
    var i = 0
    while (i < RETRY_COUNT) {
      if (isDownloadCanceled) {
        break
      }

      var url = urlString
      // if we retry and it's not the first time, try the fallback url
      if (fallbackByDefault || i > 0) {
        url = urlUtil.fallbackUrl(url)

        // want to wait before retrying again
        try {
          Thread.sleep(WAIT_TIME.toLong())
        } catch (exception: InterruptedException) {
          // no op
        }
        notifier.resetNotifications()
      }

      wifiLock.acquire()
      res = startDownload(url, destination, outputFile, details)
      if (wifiLock.isHeld) {
        wifiLock.release()
      }

      if (res == DOWNLOAD_SUCCESS) {
        // if it succeeds after the first time, always use the fallback url
        fallbackByDefault = (i > 0)
        return true
      } else if (res == QuranDownloadNotifier.ERROR_DISK_SPACE ||
        res == QuranDownloadNotifier.ERROR_PERMISSIONS
      ) {
        // critical errors
        notifier.notifyError(res, true, outputFile, details)
        return false
      } else if (res == QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD) {
        // corrupted download
        if (!previouslyCorrupted) {
          // give one more chance if this is the first time
          // this file was corrupted
          i--
          previouslyCorrupted = true
        }

        if (i + 1 < RETRY_COUNT) {
          notifyError(res, false, outputFile, details)
        }
      }
      i++
    }

    if (isDownloadCanceled) {
      res = QuranDownloadNotifier.ERROR_CANCELLED
    }
    notifyError(res, true, outputFile, details)
    return false
  }

  private fun startDownload(
    url: String, path: String,
    filename: String, notificationInfo: NotificationDetails
  ): Int {
    if (!QuranUtils.haveInternet(this)) {
      notifyError(QuranDownloadNotifier.ERROR_NETWORK, false, filename, notificationInfo)
      return QuranDownloadNotifier.ERROR_NETWORK
    }
    val result = downloadUrl(url, path, filename, notificationInfo)
    if (result == DOWNLOAD_SUCCESS) {
      if (filename.endsWith("zip")) {
        val actualFile = File(path, filename)
        if (!ZipUtils.unzipFile(
            actualFile, File(path), notificationInfo,
            this
          )
        ) {
          return if (!actualFile.delete()) QuranDownloadNotifier.ERROR_PERMISSIONS else QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD
        } else {
          actualFile.delete()
        }
      }
    }
    return result
  }

  private fun downloadUrl(
    url: String, path: String, filename: String,
    notificationInfo: NotificationDetails
  ): Int {
    Timber.d("downloading %s", url)
    val builder: Request.Builder = Request.Builder()
      .url(url).tag(DEFAULT_TAG)
    val partialFile = File(path, filename + PARTIAL_EXT)
    val actualFile = File(path, filename)

    Timber.d(
      "downloadUrl: trying to download - file %s",
      if (actualFile.exists()) "exists" else "doesn't exist"
    )
    var downloadedAmount: Long = 0
    if (partialFile.exists()) {
      downloadedAmount = partialFile.length()
      Timber.d("downloadUrl: partialFile exists, length: %d", downloadedAmount)
      builder.addHeader("Range", "bytes=$downloadedAmount-")
    }
    val isZip = filename.endsWith(".zip")

    var call: Call? = null
    var source: BufferedSource? = null
    try {
      val request: Request = builder.build()
      call = okHttpClient.newCall(request)
      val response = call.execute()
      if (response.isSuccessful) {
        Timber.d("successful response: " + response.code + " - " + downloadedAmount)
        val sink = partialFile.appendingSink().buffer()
        val body = response.body
        source = body!!.source()
        val size = body.contentLength() + downloadedAmount

        if (!isSpaceAvailable(size + (if (isZip) downloadedAmount + size else 0))) {
          return QuranDownloadNotifier.ERROR_DISK_SPACE
        } else if (actualFile.exists()) {
          if (actualFile.length() == (size + downloadedAmount)) {
            // we already downloaded, why are we re-downloading?
            return DOWNLOAD_SUCCESS
          } else if (!actualFile.delete()) {
            return QuranDownloadNotifier.ERROR_PERMISSIONS
          }
        }

        var read = 0L
        var loops = 0
        var totalRead = downloadedAmount

        while (!isDownloadCanceled && !source.exhausted() &&
          ((source.read(sink.buffer, BUFFER_SIZE.toLong()).also { read = it }) > 0)
        ) {
          totalRead += read
          if (loops++ % 5 == 0) {
            lastSentIntent = notifier.notifyProgress(notificationInfo, totalRead, size)
          }
          sink.flush()
        }
        sink.closeQuietly()

        if (isDownloadCanceled) {
          return QuranDownloadNotifier.ERROR_CANCELLED
        } else if (!partialFile.renameTo(actualFile)) {
          return notifyError(
            QuranDownloadNotifier.ERROR_PERMISSIONS,
            true,
            filename,
            notificationInfo
          )
        }
        return DOWNLOAD_SUCCESS
      } else if (response.code == 416) {
        if (!partialFile.delete()) {
          return QuranDownloadNotifier.ERROR_PERMISSIONS
        }
        return downloadUrl(url, path, filename, notificationInfo)
      } else {
        Timber.e(Exception("Unable to download file - code: " + response.code))
      }
    } catch (exception: IOException) {
      Timber.d(exception, "Failed to download file")
    } catch (se: SecurityException) {
      Timber.e(se, "Security exception while downloading file")
    } finally {
      source.closeQuietly()
    }

    return if ((call != null && call.isCanceled())) QuranDownloadNotifier.ERROR_CANCELLED else notifyError(
      QuranDownloadNotifier.ERROR_NETWORK,
      false, filename, notificationInfo
    )
  }

  override fun onProcessingProgress(details: NotificationDetails, processed: Int, total: Int) {
    if (details.totalFiles == 1) {
      lastSentIntent = notifier.notifyDownloadProcessing(
        details, processed, total
      )
    }
  }

  private fun notifyError(
    errorCode: Int,
    isFatal: Boolean,
    filename: String,
    details: NotificationDetails
  ): Int {
    lastSentIntent = notifier.notifyError(errorCode, isFatal, filename, details)

    if (isFatal) {
      // write last error in prefs
      quranSettings.setLastDownloadError(details.key, errorCode)
    }
    return errorCode
  }

  // TODO: this is actually a bug - we may not be using /sdcard...
  // we may not have permission, etc - some devices get a IllegalArgumentException
  // because the path passed is /storage/emulated/0, for example.
  private fun isSpaceAvailable(spaceNeeded: Long): Boolean {
    try {
      val fsStats = StatFs(
        Environment.getExternalStorageDirectory().absolutePath
      )
      val availableSpace = fsStats.availableBlocksLong * fsStats.blockSizeLong

      return availableSpace > spaceNeeded
    } catch (e: Exception) {
      Timber.e(e)
      return true
    }
  }

  companion object {
    const val TAG: String = "QuranDownloadService"
    const val DEFAULT_TAG: String = "QuranDownload"

    // intent actions
    const val ACTION_DOWNLOAD_URL: String = "com.quran.labs.androidquran.DOWNLOAD_URL"
    const val ACTION_CANCEL_DOWNLOADS: String = "com.quran.labs.androidquran.CANCEL_DOWNLOADS"
    const val ACTION_RECONNECT: String = "com.quran.labs.androidquran.RECONNECT"

    // extras
    const val EXTRA_URL: String = "url"
    const val EXTRA_DESTINATION: String = "destination"
    const val EXTRA_NOTIFICATION_NAME: String = "notificationName"
    const val EXTRA_DOWNLOAD_KEY: String = "downloadKey"
    const val EXTRA_REPEAT_LAST_ERROR: String = "repeatLastError"
    const val EXTRA_DOWNLOAD_TYPE: String = "downloadType"
    const val EXTRA_OUTPUT_FILE_NAME: String = "outputFileName"
    const val EXTRA_METADATA: String = "metadata"

    // extras for range downloads
    const val EXTRA_START_VERSE: String = "startVerse"
    const val EXTRA_END_VERSE: String = "endVerse"
    const val EXTRA_DOWNLOAD_DATABASE: String = "downloadDatabase"
    const val EXTRA_AUDIO_BATCH: String = "batchAudio"
    const val EXTRA_IS_GAPLESS: String = "isGapless"

    // download types (also handler message types)
    const val DOWNLOAD_TYPE_UNDEF: Int = 0
    const val DOWNLOAD_TYPE_PAGES: Int = 1
    const val DOWNLOAD_TYPE_AUDIO: Int = 2
    const val DOWNLOAD_TYPE_TRANSLATION: Int = 3
    const val DOWNLOAD_TYPE_ARABIC_SEARCH_DB: Int = 4

    // continuation of handler message types
    const val NO_OP: Int = 9

    // error prefs
    const val PREF_LAST_DOWNLOAD_ERROR: String = "lastDownloadError"
    const val PREF_LAST_DOWNLOAD_ITEM: String = "lastDownloadItem"

    const val BUFFER_SIZE: Int = 4096 * 2
    private const val WAIT_TIME = 15 * 1000
    private const val RETRY_COUNT = 3
    private const val PARTIAL_EXT = ".part"

    // download method return values
    private const val DOWNLOAD_SUCCESS = 0

    private var fallbackByDefault = false

    private fun getFilenameFromUrl(url: String): String {
      val slashIndex = url.lastIndexOf("/")
      if (slashIndex != -1) {
        return url.substring(slashIndex + 1)
      }

      // should never happen
      return url
    }
  }
}
