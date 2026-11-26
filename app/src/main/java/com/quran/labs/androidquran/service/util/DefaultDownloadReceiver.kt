package com.quran.labs.androidquran.service.util

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.service.QuranDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@Suppress("DEPRECATION")
class DefaultDownloadReceiver(
  private val context: Context,
  private val downloadType: Int
) {
  private var listener: SimpleDownloadListener? = null
  private var progressDialog: ProgressDialog? = null
  private val decimalFormat: DecimalFormat by lazy { DecimalFormat("###.00") }
  @Volatile
  private var didReceiveEvent: Boolean = false
  private var canCancelDownload: Boolean = false
  private var collectionJob: Job? = null

  fun setCanCancelDownload(canCancel: Boolean) {
    canCancelDownload = canCancel
  }

  fun register(lifecycleOwner: LifecycleOwner) {
    register(lifecycleOwner.lifecycleScope)
  }

  fun register(scope: CoroutineScope) {
    collectionJob?.cancel()
    collectionJob = scope.launch(Dispatchers.Main.immediate) {
      DownloadProgressBus.events
        .filter { it.downloadType == downloadType }
        .collect { event ->
          onEvent(event)
        }
    }
  }

  fun unregister() {
    collectionJob?.cancel()
    collectionJob = null
    setListener(null)
  }

  @VisibleForTesting
  internal fun onEvent(event: DownloadProgressEvent) {
    if (event.downloadType != downloadType) {
      return
    }
    didReceiveEvent = true
    val currentListener = listener ?: return

    when (event) {
      is DownloadProgressEvent.Success -> {
        dismissDialog()
        currentListener.handleDownloadSuccess()
      }
      is DownloadProgressEvent.Error -> {
        val msgId = ServiceIntentHelper.getErrorResourceFromErrorCode(event.errorCode, true)
        if (event.isFatal) {
          dismissDialog()
          currentListener.handleDownloadFailure(msgId)
        } else if (currentListener is DownloadListener) {
          currentListener.handleDownloadTemporaryError(msgId)
        } else {
          handleNonFatalError(msgId)
        }
      }
      is DownloadProgressEvent.Downloading -> {
        if (currentListener is DownloadListener) {
          currentListener.updateDownloadProgress(
            event.progress,
            event.downloadedSize,
            event.totalSize
          )
        } else {
          updateDownloadProgress(
            event.progress,
            event.downloadedSize,
            event.totalSize,
            event.sura,
            event.ayah
          )
        }
      }
      is DownloadProgressEvent.Processing -> {
        if (currentListener is DownloadListener) {
          currentListener.updateProcessingProgress(
            event.progress,
            event.processedFiles,
            event.totalFiles
          )
        } else {
          updateProcessingProgress(
            event.progress,
            event.processedFiles,
            event.totalFiles
          )
        }
      }
    }
  }

  private fun dismissDialog() {
    progressDialog?.let { dialog ->
      try {
        dialog.dismiss()
      } catch (_: Exception) {
        // no op
      }
      progressDialog = null
    }
  }

  fun didReceiveBroadcast(): Boolean = didReceiveEvent

  private fun makeAndShowProgressDialog() {
    makeProgressDialog()
    progressDialog?.show()
  }

  private fun makeProgressDialog() {
    if (progressDialog == null) {
      val dialog = ProgressDialog(context)
      dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
      dialog.setCancelable(canCancelDownload)
      dialog.setCanceledOnTouchOutside(false)
      if (canCancelDownload) {
        dialog.setOnCancelListener { cancelDownload() }
        dialog.setButton(
          DialogInterface.BUTTON_NEGATIVE,
          context.getString(com.quran.mobile.common.ui.core.R.string.cancel)
        ) { _, _ -> cancelDownload() }
      }

      dialog.setTitle(com.quran.mobile.common.download.R.string.downloading)
      dialog.setMessage(context.getString(R.string.downloading_message))
      progressDialog = dialog
    }
  }

  private fun cancelDownload() {
    val intent = Intent(context, QuranDownloadService::class.java).apply {
      action = QuranDownloadService.ACTION_CANCEL_DOWNLOADS
    }
    context.startService(intent)
  }

  private fun updateDownloadProgress(
    progress: Int,
    downloadedSize: Long,
    totalSize: Long,
    currentSura: Int,
    currentAyah: Int
  ) {
    if (progressDialog == null) {
      makeAndShowProgressDialog()
    }
    val dialog = progressDialog ?: return
    if (!dialog.isShowing) {
      dialog.show()
    }
    if (progress == -1) {
      val titleId = com.quran.mobile.common.download.R.string.downloading
      dialog.isIndeterminate = true
      dialog.setMessage(context.getString(titleId))
      return
    }

    dialog.isIndeterminate = false
    dialog.max = 100
    dialog.progress = progress

    val mb = 1024 * 1024
    val downloaded = context.getString(
      R.string.prefs_megabytes_str,
      decimalFormat.format(1.0 * downloadedSize / mb)
    )
    val total = context.getString(
      R.string.prefs_megabytes_str,
      decimalFormat.format(1.0 * totalSize / mb)
    )

    val message = when {
      currentSura < 1 -> String.format(
        context.getString(com.quran.mobile.feature.downloadmanager.R.string.download_progress),
        downloaded,
        total
      )
      currentAyah <= 0 -> String.format(
        context.getString(com.quran.mobile.feature.downloadmanager.R.string.download_sura_progress),
        downloaded,
        total,
        currentSura
      )
      else -> String.format(
        context.getString(com.quran.mobile.feature.downloadmanager.R.string.download_sura_ayah_progress),
        currentSura,
        currentAyah
      )
    }
    dialog.setMessage(message)
  }

  private fun updateProcessingProgress(
    progress: Int,
    processedFiles: Int,
    totalFiles: Int
  ) {
    if (progressDialog == null) {
      makeAndShowProgressDialog()
    }
    val dialog = progressDialog ?: return
    if (!dialog.isShowing) {
      dialog.show()
    }
    if (progress == -1) {
      val titleId = R.string.extracting_title
      dialog.isIndeterminate = true
      dialog.setMessage(context.getString(titleId))
      return
    }

    dialog.isIndeterminate = false
    dialog.max = 100
    dialog.progress = progress

    val message = String.format(
      context.getString(R.string.process_progress),
      processedFiles,
      totalFiles
    )
    dialog.setMessage(message)
  }

  private fun handleNonFatalError(msgId: Int) {
    if (progressDialog == null) {
      makeAndShowProgressDialog()
    }
    val dialog = progressDialog ?: return
    if (!dialog.isShowing) {
      dialog.show()
    }
    dialog.setMessage(context.getString(msgId))
  }

  fun setListener(listener: SimpleDownloadListener?) {
    this.listener = listener
    if (listener == null) {
      dismissDialog()
    }
  }

  interface SimpleDownloadListener {
    fun handleDownloadSuccess()
    fun handleDownloadFailure(errId: Int)
  }

  interface DownloadListener : SimpleDownloadListener {
    fun updateDownloadProgress(progress: Int, downloadedSize: Long, totalSize: Long)
    fun updateProcessingProgress(progress: Int, processFiles: Int, totalFiles: Int)
    fun handleDownloadTemporaryError(errorId: Int)
  }
}
