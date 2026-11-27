package com.quran.labs.androidquran.service.util

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.mobile.common.download.DownloadInfoStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class DownloadProgressBusTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `DefaultDownloadReceiver filters by downloadType delivers progress and stops after unregister`() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    try {
      val receiver = DefaultDownloadReceiver(context, QuranDownloadService.DOWNLOAD_TYPE_PAGES)
      val progressCalls = mutableListOf<Triple<Int, Long, Long>>()
      val processingCalls = mutableListOf<Triple<Int, Int, Int>>()
      val tempErrors = mutableListOf<Int>()
      var successes = 0
      val fatalErrors = mutableListOf<Int>()

      receiver.setListener(object : DefaultDownloadReceiver.DownloadListener {
        override fun updateDownloadProgress(progress: Int, downloadedSize: Long, totalSize: Long) {
          progressCalls.add(Triple(progress, downloadedSize, totalSize))
        }

        override fun updateProcessingProgress(progress: Int, processFiles: Int, totalFiles: Int) {
          processingCalls.add(Triple(progress, processFiles, totalFiles))
        }

        override fun handleDownloadTemporaryError(errorId: Int) {
          tempErrors.add(errorId)
        }

        override fun handleDownloadSuccess() {
          successes++
        }

        override fun handleDownloadFailure(errId: Int) {
          fatalErrors.add(errId)
        }
      })
      receiver.register(scope)

      // Unrelated download type must be ignored completely
      DownloadProgressBus.emit(
        DownloadProgressEvent.Downloading(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_AUDIO,
          progress = 50,
          downloadedSize = 500,
          totalSize = 1000
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(receiver.didReceiveBroadcast()).isFalse()
      assertThat(progressCalls).isEmpty()

      // Matching download progress, processing, temp error, success, fatal error
      DownloadProgressBus.emit(
        DownloadProgressEvent.Downloading(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          downloadKey = "pages",
          progress = 42,
          downloadedSize = 420,
          totalSize = 1000
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(receiver.didReceiveBroadcast()).isTrue()
      assertThat(progressCalls).containsExactly(Triple(42, 420L, 1000L))

      DownloadProgressBus.emit(
        DownloadProgressEvent.Processing(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          progress = 80,
          processedFiles = 8,
          totalFiles = 10
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(processingCalls).containsExactly(Triple(80, 8, 10))

      DownloadProgressBus.emit(
        DownloadProgressEvent.Error(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          errorCode = QuranDownloadNotifier.ERROR_NETWORK,
          isFatal = false
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(tempErrors).containsExactly(R.string.download_error_network_retry)

      DownloadProgressBus.emit(
        DownloadProgressEvent.Success(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          downloadKey = "pages"
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(successes).isEqualTo(1)

      DownloadProgressBus.emit(
        DownloadProgressEvent.Error(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          errorCode = QuranDownloadNotifier.ERROR_DISK_SPACE,
          isFatal = true
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(fatalErrors).containsExactly(R.string.download_error_disk)

      // After unregister, no further events should be delivered
      receiver.unregister()
      DownloadProgressBus.emit(
        DownloadProgressEvent.Success(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          downloadKey = "pages"
        )
      )
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(successes).isEqualTo(1)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `QuranDownloadNotifierImpl emits typed DownloadProgressEvents directly to DownloadProgressBus`() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val service = Robolectric.buildService(DummyDownloadService::class.java).create().get()
    try {
      val streams = DownloadInfoStreams()
      val notifier = QuranDownloadNotifierImpl(context, service, streams)
      val details = QuranDownloadNotifier.NotificationDetails(
        "Test Download",
        "key_1",
        QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION,
        null
      ).apply {
        setFileStatus(1, 1)
      }

      val receiver = DefaultDownloadReceiver(context, QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION)
      var gotSuccess = false
      var lastProgress = -1
      receiver.setListener(object : DefaultDownloadReceiver.DownloadListener {
        override fun updateDownloadProgress(progress: Int, downloadedSize: Long, totalSize: Long) {
          lastProgress = progress
        }
        override fun updateProcessingProgress(progress: Int, processFiles: Int, totalFiles: Int) = Unit
        override fun handleDownloadTemporaryError(errorId: Int) = Unit
        override fun handleDownloadSuccess() {
          gotSuccess = true
        }
        override fun handleDownloadFailure(errId: Int) = Unit
      })
      receiver.register(scope)

      val progressEvent = notifier.notifyProgress(details, 500, 1000)
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(progressEvent).isInstanceOf(DownloadProgressEvent.Downloading::class.java)
      assertThat((progressEvent as DownloadProgressEvent.Downloading).progress).isEqualTo(50)
      assertThat(lastProgress).isEqualTo(50)

      val processingEvent = notifier.notifyDownloadProcessing(details, 3, 6)
      assertThat(processingEvent).isInstanceOf(DownloadProgressEvent.Processing::class.java)
      assertThat((processingEvent as DownloadProgressEvent.Processing).progress).isEqualTo(50)

      val successEvent = notifier.notifyDownloadSuccessful(details)
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(successEvent).isInstanceOf(DownloadProgressEvent.Success::class.java)
      assertThat(gotSuccess).isTrue()

      val errorEvent = notifier.notifyError(
        QuranDownloadNotifier.ERROR_NETWORK,
        isFatal = true,
        filename = "file.db",
        details = details
      )
      assertThat(errorEvent).isInstanceOf(DownloadProgressEvent.Error::class.java)
      assertThat((errorEvent as DownloadProgressEvent.Error).isFatal).isTrue()
      assertThat(errorEvent.errorCode).isEqualTo(QuranDownloadNotifier.ERROR_NETWORK)

      receiver.unregister()
    } finally {
      service.onDestroy()
      scope.cancel()
    }
  }

  @Test
  fun `burst of Downloading Success Downloading never drops terminal Success event`() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    try {
      val receiver = DefaultDownloadReceiver(context, QuranDownloadService.DOWNLOAD_TYPE_PAGES)
      val events = mutableListOf<String>()
      receiver.setListener(object : DefaultDownloadReceiver.DownloadListener {
        override fun updateDownloadProgress(progress: Int, downloadedSize: Long, totalSize: Long) {
          events.add("progress:$progress")
        }
        override fun updateProcessingProgress(progress: Int, processFiles: Int, totalFiles: Int) = Unit
        override fun handleDownloadTemporaryError(errorId: Int) = Unit
        override fun handleDownloadSuccess() {
          events.add("success")
        }
        override fun handleDownloadFailure(errId: Int) {
          events.add("error:$errId")
        }
      })
      receiver.register(scope)
      // Let collector subscribe on Main before emitting burst
      shadowOf(Looper.getMainLooper()).idle()

      // Emit back-to-back before Main drains the queue
      DownloadProgressBus.emit(
        DownloadProgressEvent.Downloading(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          progress = 90,
          downloadedSize = 900,
          totalSize = 1000
        )
      )
      DownloadProgressBus.emit(
        DownloadProgressEvent.Success(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          downloadKey = "batch1"
        )
      )
      DownloadProgressBus.emit(
        DownloadProgressEvent.Downloading(
          downloadType = QuranDownloadService.DOWNLOAD_TYPE_PAGES,
          progress = 5,
          downloadedSize = 50,
          totalSize = 1000
        )
      )
      shadowOf(Looper.getMainLooper()).idle()

      assertThat(events).containsExactly("progress:90", "success", "progress:5").inOrder()
      receiver.unregister()
    } finally {
      scope.cancel()
    }
  }

  class DummyDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
  }
}
