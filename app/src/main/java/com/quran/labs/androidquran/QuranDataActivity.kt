package com.quran.labs.androidquran

import android.Manifest.permission
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.model.QuranDataStatus
import com.quran.labs.androidquran.presenter.data.QuranDataPresenter
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.PermissionUtil
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.worker.WorkerConstants
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.inject.Inject

/**
 * Launch [QuranActivity] after performing the following checks:
 *
 *  * Check that we have permission to write to external storage (if we need this permission)
 * and if not, ask the user for permission
 *  * Verify that we have the necessary Quran data downloaded on the device
 *
 * The logic is split between [QuranDataActivity] and [QuranDataPresenter],
 * and [QuranDownloadService] is (mostly) used to perform the actual downloading of
 * any Quran data.
 */
class QuranDataActivity : AppCompatActivity(), SimpleDownloadListener, OnRequestPermissionsResultCallback {

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var quranScreenInfo: QuranScreenInfo

  @Inject
  lateinit var quranDataPresenter: QuranDataPresenter

  @Inject
  lateinit var preferencesUpgrade: PreferencesUpgrade

  private lateinit var quranSettings: QuranSettings

  private var errorDialog: AlertDialog? = null
  private var promptForDownloadDialog: AlertDialog? = null
  private var permissionsDialog: AlertDialog? = null
  private var downloadReceiver: DefaultDownloadReceiver? = null
  private var quranDataStatus: QuranDataStatus? = null
  private var updateDialog: AlertDialog? = null
  private var disposable: Disposable? = null
  private var lastForceValue: Boolean = false
  private var didCheckPermissions: Boolean = false

  private val scope = MainScope()

  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val quranApp = application as QuranApplication
    quranApp.applicationComponent.inject(this)
    quranSettings = QuranSettings.getInstance(this)
    quranSettings.upgradePreferences(preferencesUpgrade)
  }

  override fun onResume() {
    super.onResume()
    quranDataPresenter.bind(this)
    val downloadReceiver = DefaultDownloadReceiver(
        this,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES
    )
    downloadReceiver.setCanCancelDownload(true)
    val action = ProgressIntent.INTENT_NAME
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            downloadReceiver,
            IntentFilter(action)
        )
    downloadReceiver.setListener(this)
    this.downloadReceiver = downloadReceiver

    disposable = Single.timer(100, MILLISECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { _: Long? ->
          // try to reconnect to the service - delayed since some devices consider
          // onResume as part of the app being in the background for starting-service
          // purposes.
          val reconnectIntent = Intent(this@QuranDataActivity, QuranDownloadService::class.java)
          reconnectIntent.action = QuranDownloadService.ACTION_RECONNECT
          reconnectIntent.putExtra(
              QuranDownloadService.EXTRA_DOWNLOAD_TYPE,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES
          )
          try {
            // this should be perfectly valid - on some devices though, despite the 100ms
            // delay (or a longer one), some devices still complain about not being in the
            // foreground.
            //
            // since this isn't strictly necessary (since the download service should emit
            // periodically on its own anyway, and should handle multiple download requests,
            // etc), let's just drop it if it fails since this is expected to rarely occur.
            startService(reconnectIntent)
          } catch (ise: IllegalStateException) {
            Timber.e(ise)
          }
        }

    if (!didCheckPermissions) {
      didCheckPermissions = true
      checkPermissions()
    }
  }

  override fun onPause() {
    disposable?.dispose()
    quranDataPresenter.unbind(this)
    downloadReceiver?.let { receiver ->
      receiver.setListener(null)
      LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
      downloadReceiver = null
    }

    promptForDownloadDialog?.dismiss()
    promptForDownloadDialog = null

    errorDialog?.dismiss()
    errorDialog = null
    hideMigrationDialog()

    super.onPause()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun checkPermissions() {
    // path is the current app location - if not set, it falls back to filesDir
    val path = quranSettings.appCustomLocation
    val fallbackFile = filesDir
    val usesInternalDir = path == fallbackFile.absolutePath
    val usesExternalFileDir =
        ContextCompat.getExternalFilesDirs(this, null).any { file: File? ->
          file?.absolutePath == path
        }

    val needsPermission = (!usesExternalFileDir && !usesInternalDir)
    if (needsPermission && !PermissionUtil.haveWriteExternalStoragePermission(this)) {
      // we need permission and don't have it, so fall back to internal storage
      quranSettings.appCustomLocation = fallbackFile.absolutePath
      checkPages()
   } else if (needsPermission && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      // we need permission (i.e. are writing to the sdcard) on Android 11 and above
      // and we have it, but we should migrate because we target Android 11, which means
      // new installations there must use scoped storage.
      migrateFromTo(fallbackFile.absolutePath)
    } else {
      checkPages()
    }
  }

  private fun showMigrationDialog() {
    if (updateDialog == null) {
      val migrationDialog = AlertDialog.Builder(this)
        .setView(R.layout.migration_upgrade)
        .setCancelable(false)
        .create()
      updateDialog = migrationDialog
      migrationDialog.show()
    }
  }

  private fun hideMigrationDialog() {
    updateDialog?.dismiss()
    updateDialog = null
  }

  private fun migrateFromTo(destination: String) {
    showMigrationDialog()

    scope.launch {
      withContext(Dispatchers.IO) {
        if (quranFileUtils.moveAppFiles(applicationContext, destination)) {
          // only if we succeed...
          quranSettings.appCustomLocation = destination
        }
      }
      checkPages()
    }
  }

  private fun checkPages() {
    showMigrationDialog()
    quranDataPresenter.checkPages()
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun requestPostNotificationPermission() {
    ActivityCompat.requestPermissions(
      this, arrayOf(permission.POST_NOTIFICATIONS),
      REQUEST_POST_NOTIFICATION_PERMISSIONS
    )
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_POST_NOTIFICATION_PERMISSIONS) {
      actuallyDownloadQuranImages(lastForceValue)
    }
  }

  override fun handleDownloadSuccess() {
    if (quranDataStatus != null && !quranDataStatus!!.havePages()) {
      // didn't have pages before and the download succeeded, which means
      // full zip download was done - let's mark partial pages checker as
      // not needed since we already checked it.
      val pageType = quranSettings.pageType
      quranSettings.setCheckedPartialImages(pageType)

      // cancel any pending work
      WorkManager.getInstance(applicationContext)
          .cancelUniqueWork(WorkerConstants.CLEANUP_PREFIX + pageType)
    }
    quranSettings.removeShouldFetchPages()
    runListView()
  }

  override fun handleDownloadFailure(errId: Int) {
    if (errorDialog != null && errorDialog!!.isShowing) {
      return
    }
    showFatalErrorDialog(errId)
  }

  private fun showFatalErrorDialog(errorId: Int) {
    val builder = AlertDialog.Builder(this)
    builder.setMessage(errorId)
    builder.setCancelable(false)
    builder.setPositiveButton(
        R.string.download_retry
    ) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      errorDialog = null
      removeErrorPreferences()
      downloadQuranImages(true)
    }
    builder.setNegativeButton(
        R.string.download_cancel
    ) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      errorDialog = null
      removeErrorPreferences()
      quranSettings.setShouldFetchPages(false)
      runListViewWithoutPages()
    }
    errorDialog = builder.create()
    errorDialog!!.show()
  }

  private fun removeErrorPreferences() {
    quranSettings.clearLastDownloadError()
  }

  fun onStorageNotAvailable() {
    hideMigrationDialog()
    // no storage mounted, nothing we can do...
    runListViewWithoutPages()
  }

  fun onPagesChecked(quranDataStatus: QuranDataStatus) {
    hideMigrationDialog()

    this.quranDataStatus = quranDataStatus
    if (!quranDataStatus.havePages()) {
      val lastErrorItem = quranSettings.lastDownloadItemWithError
      Timber.d("checkPages: need to download pages... lastError: %s", lastErrorItem)
      when {
        PAGES_DOWNLOAD_KEY == lastErrorItem -> {
          val lastError = quranSettings.lastDownloadErrorCode
          val errorId = ServiceIntentHelper
              .getErrorResourceFromErrorCode(lastError, false)
          showFatalErrorDialog(errorId)
        }
        quranSettings.shouldFetchPages() -> {
          downloadQuranImages(false)
        }
        else -> {
          promptForDownload()
        }
      }
    } else {
      val patchParam = quranDataStatus.patchParam
      if (!TextUtils.isEmpty(patchParam)) {
        Timber.d("checkPages: have pages, but need patch %s", patchParam)
        promptForDownload()
      } else {
        runListView()
      }
    }
  }

  /**
   * this method asks the service to download quran images.
   *
   * there are two possible cases - the first is one in which we are not
   * sure if a download is going on or not (ie we just came in the app,
   * the files aren't all there, so we want to start downloading).  in
   * this case, we start the download only if we didn't receive any
   * broadcasts before starting it.
   *
   * in the second case, we know what we are doing (either because the user
   * just clicked "download" for the first time or the user asked to retry
   * after an error), then we pass the force parameter, which asks the
   * service to just restart the download irrespective of anything else.
   *
   * @param force whether to force the download to restart or not
   */
  private fun downloadQuranImages(force: Boolean) {
    if (PermissionUtil.havePostNotificationPermission(this)) {
      actuallyDownloadQuranImages(force)
    } else if (PermissionUtil.canRequestPostNotificationPermission(this)) {
      val dialog = PermissionUtil.buildPostPermissionDialog(
        this,
        onAccept = {
          lastForceValue = force
          permissionsDialog = null
          requestPostNotificationPermission()
        },
        onDecline = {
          permissionsDialog = null
          actuallyDownloadQuranImages(force)
        }
      )
      permissionsDialog = dialog
      dialog.show()
    } else {
      lastForceValue = force
      requestPostNotificationPermission()
    }
  }

  private fun actuallyDownloadQuranImages(force: Boolean) {
    // if any broadcasts were received, then we are already downloading
    // so unless we know what we are doing (via force), don't ask the
    // service to restart the download
    if (downloadReceiver != null && downloadReceiver!!.didReceiveBroadcast() && !force) {
      return
    }

    val dataStatus = quranDataStatus
    if (dataStatus == null) {
      // we lost the cached data status, so just check again
      checkPages()
      return
    }

    var url: String
    url = if (dataStatus.needPortrait() && !dataStatus.needLandscape()) {
      // phone (and tablet when upgrading on some devices, ex n10)
      quranFileUtils.zipFileUrl
    } else if (dataStatus.needLandscape() && !dataStatus.needPortrait()) {
      // tablet (when upgrading from pre-tablet on some devices, ex n7).
      quranFileUtils.getZipFileUrl(quranScreenInfo.tabletWidthParam)
    } else {
      // new tablet installation - if both image sets are the same
      // size, then just get the correct one only
      if (quranScreenInfo.tabletWidthParam == quranScreenInfo.widthParam) {
        quranFileUtils.zipFileUrl
      } else {
        // otherwise download one zip with both image sets
        val widthParam = quranScreenInfo.widthParam + quranScreenInfo.tabletWidthParam
        quranFileUtils.getZipFileUrl(widthParam)
      }
    }

    // if we have a patch url, just use that
    val patchParam = dataStatus.patchParam
    if (!TextUtils.isEmpty(patchParam)) {
      url = quranFileUtils.getPatchFileUrl(patchParam!!, quranDataPresenter.imagesVersion())
    }
    val destination = quranFileUtils.getQuranImagesBaseDirectory()

    // start service
    val intent = ServiceIntentHelper.getDownloadIntent(
        this, url,
        destination.absolutePath, getString(R.string.app_name), PAGES_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_PAGES
    )
    if (!force) {
      // handle race condition in which we missed the error preference and
      // the broadcast - if so, just rebroadcast errors so we handle them
      intent.putExtra(QuranDownloadService.EXTRA_REPEAT_LAST_ERROR, true)
    }
    startService(intent)
  }

  private fun promptForDownload() {
    val dataStatus = quranDataStatus ?: return
    val message = if (dataStatus.needPortrait()) {
      R.string.downloadPrompt
    } else if (quranScreenInfo.isDualPageMode && dataStatus.needLandscape()) {
      R.string.downloadTabletPrompt
    } else if (dataStatus.patchParam?.isNotEmpty() == true) {
      R.string.downloadImportantPrompt
    } else {
      R.string.downloadPrompt
    }

    val dialog = AlertDialog.Builder(this)
    dialog.setMessage(message)
    dialog.setCancelable(false)
    dialog.setPositiveButton(
        R.string.downloadPrompt_ok
    ) { dialog1: DialogInterface, _: Int ->
      dialog1.dismiss()
      promptForDownloadDialog = null
      quranSettings.setShouldFetchPages(true)
      downloadQuranImages(true)
    }
    dialog.setNegativeButton(
        R.string.downloadPrompt_no
    ) { dialog12: DialogInterface, _: Int ->
      dialog12.dismiss()
      promptForDownloadDialog = null
      val isPatch = dataStatus.patchParam?.isNotEmpty() == true
      if (isPatch) {
        // for patches, we have the pages, so we can just show the list no problem
        runListView()
      } else {
        runListViewWithoutPages()
      }
    }
    val promptForDownloadDialog = dialog.create()
    promptForDownloadDialog.setTitle(R.string.downloadPrompt_title)
    promptForDownloadDialog.show()
    this.promptForDownloadDialog = promptForDownloadDialog
  }

  private fun runListViewWithoutPages() {
    if (!quranDataPresenter.canProceedWithoutDownload()) {
      // we only have download on demand for full page images, so fallback
      quranDataPresenter.fallbackToImageType()
    }
    runListView()
  }

  private fun runListView() {
    val i = Intent(this, QuranActivity::class.java)
    i.putExtra(
        QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, quranSettings.haveUpdatedTranslations()
    )
    startActivity(i)
    finish()
  }

  companion object {
    const val PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY"
    private const val REQUEST_POST_NOTIFICATION_PERMISSIONS = 1
  }
}
