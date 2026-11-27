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
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.content.IntentCompat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.extension.closeQuietly
import com.quran.labs.androidquran.service.download.DownloadStrategy
import com.quran.labs.androidquran.service.download.GaplessDownloadStrategy
import com.quran.labs.androidquran.service.download.GappedDownloadStrategy
import com.quran.labs.androidquran.service.download.SingleFileDownloadStrategy
import com.quran.labs.androidquran.service.util.DownloadProgressBus
import com.quran.labs.androidquran.service.util.DownloadProgressEvent
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import com.quran.labs.androidquran.service.util.QuranDownloadNotifierImpl
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.UrlUtil
import com.quran.labs.androidquran.util.ZipUtils
import com.quran.labs.androidquran.util.ZipUtils.ZipListener
import com.quran.mobile.common.download.DownloadInfoStreams
import dev.zacsweers.metro.Inject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
import okio.appendingSink
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

class QuranDownloadService : Service(), ZipListener<NotificationDetails> {
  private lateinit var serviceLooper: Looper
  private lateinit var serviceHandler: ServiceHandler
  @VisibleForTesting
  internal lateinit var notifier: QuranDownloadNotifier

  // written from ui thread and read by download thread
  @Volatile
  private var isDownloadCanceled = false
  @Volatile
  private var activeCall: Call? = null
  private var fallbackByDefault = false
  private lateinit var quranSettings: QuranSettings
  private lateinit var wifiLock: WifiLock

  @Volatile
  private var lastSentEvent: DownloadProgressEvent? = null
  private val successfulZippedDownloads: MutableMap<String, Boolean> = mutableMapOf()
  private val recentlyFailedDownloads: MutableMap<String, DownloadProgressEvent> = mutableMapOf()

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
    running.set(true)
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
    notifier = QuranDownloadNotifierImpl(this, this, downloadInfoStreams)
  }

  private fun handleOnStartCommand(intent: Intent?, startId: Int) {
    if (intent != null) {
      if (ACTION_CANCEL_DOWNLOADS == intent.action) {
        serviceHandler.removeCallbacksAndMessages(null)
        isDownloadCanceled = true
        activeCall?.cancel()
        sendNoOpMessage(startId)
      } else if (ACTION_RECONNECT == intent.action) {
        val type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, DOWNLOAD_TYPE_UNDEF)
        val currentLast = lastSentEvent
        val lastType = currentLast?.downloadType ?: -1

        if (type == lastType && currentLast != null) {
          DownloadProgressBus.emit(currentLast)
        } else if (serviceHandler.hasMessages(type)) {
          DownloadProgressBus.emit(DownloadProgressEvent.Downloading(downloadType = type))
        }
        sendNoOpMessage(startId)
      } else {
        // if we are currently downloading, resend the last progress event
        // and don't queue anything
        val download = intent.getStringExtra(EXTRA_DOWNLOAD_KEY)
        val currentLast = lastSentEvent
        if (download != null && download == currentLast?.downloadKey) {
          Timber.d("resending last progress event...")
          DownloadProgressBus.emit(currentLast)

          val isTerminal = currentLast is DownloadProgressEvent.Success ||
            (currentLast is DownloadProgressEvent.Error && currentLast.isFatal)
          if (!isTerminal) {
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

  override fun onTimeout(startId: Int, fgsType: Int) {
    isDownloadCanceled = true
    activeCall?.cancel()
    serviceHandler.removeCallbacksAndMessages(null)
    // stop the service without waiting for the download thread
    stopSelf()
  }

  override fun onDestroy() {
    running.set(false)
    isDownloadCanceled = true
    activeCall?.cancel()
    // download thread releases the Wi-Fi lock
    serviceLooper.quit()
    super.onDestroy()
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

      val metadata =
        IntentCompat.getParcelableExtra<Parcelable?>(intent, EXTRA_METADATA, Parcelable::class.java)

      val details =
        NotificationDetails(notificationTitle, key, type, metadata)
      // check if already downloaded, and if so, send broadcast
      val isZipFile = url!!.endsWith(".zip")
      if (isZipFile && successfulZippedDownloads.containsKey(url)) {
        lastSentEvent = notifier.broadcastDownloadSuccessful(details)
        return
      } else if (recentlyFailedDownloads.containsKey(url)) {
        // if recently failed and we want to repeat the last error...
        if (intent.getBooleanExtra(EXTRA_REPEAT_LAST_ERROR, false)) {
          val failedEvent = recentlyFailedDownloads[url]
          if (failedEvent != null) {
            // re-emit and leave - just in case of race condition
            DownloadProgressBus.emit(failedEvent)
            return
          }
        } else {
          recentlyFailedDownloads.remove(url)
        }
      }
      notifier.resetNotifications()

      // get the start/end ayah info if it's a ranged download
      val startAyah = IntentCompat.getSerializableExtra<SuraAyah?>(
        intent,
        EXTRA_START_VERSE,
        SuraAyah::class.java
      )

      val endAyah =
        IntentCompat.getSerializableExtra<SuraAyah?>(intent, EXTRA_END_VERSE, SuraAyah::class.java)
      val isGapless = intent.getBooleanExtra(EXTRA_IS_GAPLESS, false)

      var outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME)
      if (outputFile == null) {
        outputFile = getFilenameFromUrl(url)
      }
      val destination = intent.getStringExtra(EXTRA_DESTINATION)
      lastSentEvent = null

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
        if (resolveSafeOutputFile(File(destination), outputFile) == null) {
          notifyError(QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD, true, outputFile, details)
          return
        }
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
        val lastEvent = lastSentEvent
        if (lastEvent != null) {
          recentlyFailedDownloads[url] = lastEvent
        }
      }
      lastSentEvent = null
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
    lastSentEvent = notifier.notifyProgress(details, 0, 0)

    return if (strategy.downloadFiles()) {
      if (isDownloadCanceled) {
        notifyError(QuranDownloadNotifier.ERROR_CANCELLED, true, "", details)
        false
      } else {
        lastSentEvent = notifier.notifyDownloadSuccessful(details)
        true
      }
    } else {
      false
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
      }

      if (i > 0) {
        try {
          Thread.sleep(WAIT_TIME.toLong())
        } catch (_: InterruptedException) {
          // no op
        }
        notifier.resetNotifications()
      }

      if (isDownloadCanceled) {
        break
      }
      wifiLock.acquire()
      try {
        res = startDownload(url, destination, outputFile, details)
      } finally {
        wifiLock.release()
      }

      if (isDownloadCanceled) {
        break
      }
      if (res == DOWNLOAD_SUCCESS) {
        if (url != urlString) fallbackByDefault = true
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
        // finish unzipping even if canceled
        val actualFile = resolveSafeOutputFile(File(path), filename)
          ?: return QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD
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

  @VisibleForTesting
  internal fun downloadUrl(
    url: String, path: String, filename: String,
    notificationInfo: NotificationDetails
  ): Int {
    Timber.d("downloading %s", url)
    val destDir = File(path)
    val actualFile = resolveSafeOutputFile(destDir, filename)
      ?: return notifyError(
        QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD,
        true,
        filename,
        notificationInfo
      )
    val partialFile = resolveSafeOutputFile(destDir, filename + PARTIAL_EXT)
      ?: return notifyError(
        QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD,
        true,
        filename,
        notificationInfo
      )

    Timber.d(
      "downloadUrl: trying to download - file %s",
      if (actualFile.exists()) "exists" else "doesn't exist"
    )
    val isZip = filename.endsWith(".zip")
    var retried416 = false

    while (true) {
      if (isDownloadCanceled) {
        return QuranDownloadNotifier.ERROR_CANCELLED
      }
      val builder: Request.Builder = Request.Builder()
        .url(url).tag(DEFAULT_TAG)
      var downloadedAmount: Long = 0
      if (partialFile.exists()) {
        downloadedAmount = partialFile.length()
        Timber.d("downloadUrl: partialFile exists, length: %d", downloadedAmount)
        builder.addHeader("Range", "bytes=$downloadedAmount-")
      }

      var call: Call? = null
      try {
        val request: Request = builder.build()
        call = okHttpClient.newCall(request)
        activeCall = call
        var shouldRetry416 = false

        call.execute().use { response ->
          if (response.isSuccessful) {
            Timber.d("successful response: %d - %d", response.code, downloadedAmount)
            if (downloadedAmount > 0 && response.code != 206) {
              if (partialFile.exists() && !partialFile.delete()) {
                return QuranDownloadNotifier.ERROR_PERMISSIONS
              }
              downloadedAmount = 0
            }
            val body = response.body
            val contentLength = body.contentLength()
            val size = if (contentLength >= 0) contentLength + downloadedAmount else -1L
            val requiredSpace = if (contentLength >= 0) {
              size + (if (isZip) downloadedAmount + size else 0L)
            } else {
              0L
            }

            if (requiredSpace > 0 && !isSpaceAvailable(requiredSpace, path)) {
              return QuranDownloadNotifier.ERROR_DISK_SPACE
            } else if (actualFile.exists()) {
              if (contentLength >= 0 && actualFile.length() == size) {
                // we already downloaded, why are we re-downloading?
                return DOWNLOAD_SUCCESS
              } else if (!actualFile.delete()) {
                return QuranDownloadNotifier.ERROR_PERMISSIONS
              }
            }

            val source = body.source()
            val rawSink = if (downloadedAmount > 0) {
              partialFile.appendingSink()
            } else {
              partialFile.sink()
            }
            rawSink.buffer().use { sink ->
              var lastNotifyMs = 0L
              copySourceToSink(
                source = source,
                sink = sink,
                initialDownloadedAmount = downloadedAmount,
                isCanceled = { isDownloadCanceled },
                onProgress = { totalRead ->
                  val now = SystemClock.elapsedRealtime()
                  if (now - lastNotifyMs >= PROGRESS_NOTIFY_INTERVAL_MS) {
                    lastNotifyMs = now
                    lastSentEvent = notifier.notifyProgress(notificationInfo, totalRead, size)
                  }
                }
              )
            }

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
          } else if (response.code == 416 && !retried416) {
            if (!partialFile.delete()) {
              return QuranDownloadNotifier.ERROR_PERMISSIONS
            }
            retried416 = true
            shouldRetry416 = true
          } else {
            Timber.e(Exception("Unable to download file - code: " + response.code))
          }
        }

        if (shouldRetry416) {
          continue
        }
      } catch (exception: IOException) {
        Timber.d(exception, "Failed to download file")
      } catch (se: SecurityException) {
        Timber.e(se, "Security exception while downloading file")
      } finally {
        activeCall = null
      }

      return if (isDownloadCanceled || (call != null && call.isCanceled())) {
        QuranDownloadNotifier.ERROR_CANCELLED
      } else {
        notifyError(
          QuranDownloadNotifier.ERROR_NETWORK,
          false, filename, notificationInfo
        )
      }
    }
  }

  override fun onProcessingProgress(details: NotificationDetails, processed: Int, total: Int) {
    if (details.totalFiles == 1) {
      lastSentEvent = notifier.notifyDownloadProcessing(
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
    lastSentEvent = notifier.notifyError(errorCode, isFatal, filename, details)

    if (isFatal) {
      // write last error in prefs
      quranSettings.setLastDownloadError(details.key, errorCode)
    }
    return errorCode
  }

  private fun isSpaceAvailable(spaceNeeded: Long, path: String): Boolean {
    try {
      val fsStats = try {
        StatFs(path)
      } catch (_: IllegalArgumentException) {
        StatFs(Environment.getExternalStorageDirectory().absolutePath)
      }
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
    private const val PROGRESS_NOTIFY_INTERVAL_MS = 150L
    private const val WAIT_TIME = 15 * 1000
    private const val RETRY_COUNT = 3
    private const val PARTIAL_EXT = ".part"

    // download method return values
    private const val DOWNLOAD_SUCCESS = 0

    private val running = AtomicBoolean(false)

    @JvmStatic
    fun isRunning(): Boolean = running.get()

    internal inline fun copySourceToSink(
      source: BufferedSource,
      sink: BufferedSink,
      initialDownloadedAmount: Long,
      isCanceled: () -> Boolean,
      onProgress: (totalRead: Long) -> Unit = {}
    ) {
      var read = 0L
      var totalRead = initialDownloadedAmount
      while (!isCanceled() && !source.exhausted() &&
        ((source.read(sink.buffer, BUFFER_SIZE.toLong()).also { read = it }) > 0)
      ) {
        totalRead += read
        sink.emitCompleteSegments()
        onProgress(totalRead)
      }
    }

    @VisibleForTesting
    internal fun getFilenameFromUrl(url: String): String {
      val withoutFragment = url.substringBefore('#')
      val withoutQuery = withoutFragment.substringBefore('?')
      val slashIndex = withoutQuery.lastIndexOf("/")
      if (slashIndex != -1) {
        return withoutQuery.substring(slashIndex + 1)
      }

      // should never happen
      return withoutQuery
    }

    @VisibleForTesting
    internal fun resolveSafeOutputFile(baseDir: File, filename: String): File? {
      if (filename.isBlank() ||
        filename == "." ||
        filename == ".." ||
        filename.contains('/') ||
        filename.contains('\\') ||
        filename.contains("..")
      ) {
        return null
      }
      val baseCanonical = baseDir.canonicalPath
      val basePrefix = if (baseCanonical.endsWith(File.separator)) {
        baseCanonical
      } else {
        baseCanonical + File.separator
      }
      val target = File(baseDir, filename)
      return if (target.canonicalPath.startsWith(basePrefix)) target else null
    }
  }
}
