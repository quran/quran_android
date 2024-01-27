package com.quran.labs.androidquran

import android.Manifest.permission
import android.R.string
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
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
import com.quran.labs.androidquran.ui.util.ToastCompat
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
import java.io.IOException
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
class QuranDataActivity : Activity(), SimpleDownloadListener, OnRequestPermissionsResultCallback {

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

    // replace null app locations (especially those set to null due to failures
    // of finding a suitable data directory) with the default value to allow
    // retrying to find a suitable location on app startup.
    if (!quranSettings.isAppLocationSet) {
      quranSettings.appCustomLocation = quranSettings.defaultLocation
    }
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

    scope.cancel()
    super.onPause()
  }

  private fun checkPermissions() {
    // path is the current app location - if not set, it falls back to getExternalFilesDir
    val path = quranSettings.appCustomLocation
    val fallbackFile = filesDir
    val usesInternalDir = path != null && path == fallbackFile.absolutePath
    val usesExternalFileDir = path != null &&
        ContextCompat.getExternalFilesDirs(this, null).any { file: File? ->
          file != null && file.absolutePath == path
        }

    if (path == null) {
      // error case: suggests that we're on m+ and we have no fallback
      runListViewWithoutPages()
      return
    }

    val needsPermission = (!usesExternalFileDir && !usesInternalDir)
    if (needsPermission && !PermissionUtil.haveWriteExternalStoragePermission(this)) {
      // we need permission and don't have it, so request it if we can
      if (PermissionUtil.canRequestWriteExternalStoragePermission(this)) {
        askIfCanRequestPermissions(fallbackFile)
      } else {
        // we can't request permissions, so try to fall back
        quranSettings.appCustomLocation = fallbackFile.absolutePath
        checkPages()
      }
   } else if (needsPermission && Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      // we need permission (i.e. are writing to the sdcard) on Android 11 and above
      // we should migrate because when we are required to target Android 11 next year
      // in sha' Allah, we may lose legacy permissions.
      //
      // one question might be, "what if the person set a custom path?" - on Kitkat and
      // above, the only allowed custom paths are what is returned by getExternalFilesDirs,
      // none of which need write permissions. it's extremely unlikely for someone to upgrade
      // from Kitkat to Android 10 (10 years worth of upgrades!)
      migrateFromTo(fallbackFile.absolutePath)
    } else {
      checkPages()
    }
  }

  private fun askIfCanRequestPermissions(fallbackFile: File?) {
    //show permission rationale dialog
    val permissionsDialog = AlertDialog.Builder(this)
        .setMessage(R.string.storage_permission_rationale)
        .setCancelable(false)
        .setPositiveButton(string.ok) { dialog: DialogInterface, _: Int ->
          dialog.dismiss()
          permissionsDialog = null

          // request permissions
          requestExternalSdcardPermission()
        }
        .setNegativeButton(string.cancel) { dialog: DialogInterface, _: Int ->
          // dismiss the dialog
          dialog.dismiss()
          permissionsDialog = null

          // fall back if we can
          if (fallbackFile != null) {
            quranSettings.appCustomLocation = fallbackFile.absolutePath
            checkPages()
          } else {
            // set to null so we can try again next launch
            quranSettings.appCustomLocation = null
            runListViewWithoutPages()
          }
        }
        .create()
    this.permissionsDialog = permissionsDialog
    permissionsDialog.show()
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

  private fun requestExternalSdcardPermission() {
    ActivityCompat.requestPermissions(
        this, arrayOf(permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_WRITE_TO_SDCARD_PERMISSIONS
    )
    quranSettings.setSdcardPermissionsDialogPresented()
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
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        /*
         * taking a risk here. on nexus 6, the permission is granted automatically. on the emulator,
         * a restart is required. on reddit, someone with a nexus 9 said they also didn't need to
         * restart for the permission to take effect.
         *
         * going to assume that it just works to avoid meh code (a check to see if i can actually
         * write, and a PendingIntent plus System.exit to restart the app otherwise). logging to
         * know if we should actually have that code or not.
         *
         * also see:
         * http://stackoverflow.com/questions/32471888/
         */
        if (!canWriteSdcardAfterPermissions()) {
          ToastCompat.makeText(
              this,
              R.string.storage_permission_please_restart, Toast.LENGTH_LONG
          )
              .show()
        }
        checkPages()
      } else {
        val fallbackFile = getExternalFilesDir(null)
        if (fallbackFile != null) {
          quranSettings.appCustomLocation = fallbackFile.absolutePath
          checkPages()
        } else {
          // set to null so we can try again next launch
          quranSettings.appCustomLocation = null
          runListViewWithoutPages()
        }
      }
    } else if (requestCode == REQUEST_POST_NOTIFICATION_PERMISSIONS) {
      actuallyDownloadQuranImages(lastForceValue)
    }
  }

  private fun canWriteSdcardAfterPermissions(): Boolean {
    val location = quranFileUtils.getQuranBaseDirectory(this)
    if (location != null) {
      try {
        if (File(location).exists() ||
            quranFileUtils.makeQuranDirectory(this, quranScreenInfo.widthParam)
        ) {
          val f = File(location, "" + System.currentTimeMillis())
          if (f.createNewFile()) {
            f.delete()
            return true
          }
        }
      } catch (e: Exception) {
        // no op
      }
    }
    return false
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
      if (quranSettings.didDownloadPages()) {
        // log if we downloaded pages once before
        try {
          onPagesLost()
        } catch (e: Exception) {
          Timber.e(e)
        }

        // pages are lost, switch to internal storage if we aren't on it
        val path = quranSettings.appCustomLocation
        val internalDirectory = filesDir.absolutePath
        if (path != internalDirectory) {
          quranSettings.appCustomLocation = internalDirectory
        }

        // clear the "pages downloaded" flag
        quranSettings.removeDidDownloadPages()
      }
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
      val appLocation = quranSettings.appCustomLocation
      val baseDirectory = quranFileUtils.quranBaseDirectory
      try {
        // try to write a directory to distinguish between the entire Quran directory
        // being removed versus just the images being somehow removed.
        File(baseDirectory, QURAN_DIRECTORY_MARKER_FILE).createNewFile()
        File(baseDirectory, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).createNewFile()

        // try writing a file to the app's internal no_backup directory
        File(noBackupFilesDir, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).createNewFile()
        quranSettings.setDownloadedPages(
            System.currentTimeMillis(), appLocation,
            quranDataStatus.portraitWidth + "_" + quranDataStatus.landscapeWidth
        )
      } catch (ioe: IOException) {
        Timber.e(ioe)
      }
      val patchParam = quranDataStatus.patchParam
      if (!TextUtils.isEmpty(patchParam)) {
        Timber.d("checkPages: have pages, but need patch %s", patchParam)
        promptForDownload()
      } else {
        runListView()
      }
    }
  }

  private fun onPagesLost() {
    val appLocation = quranSettings.appCustomLocation
    // check if appLocation matches either external files dir or the sdcard
    val appDir = getExternalFilesDir(null)
    val sdcard = Environment.getExternalStorageDirectory()

    // see if the page path at the time of the first download hasn't changed
    val lastDownloadedPagePath = quranSettings.previouslyDownloadedPath
    val isPagePathTheSame = appLocation == lastDownloadedPagePath

    // see if the last downloaded page types are the same as what we want to download now
    val lastDownloadedPages = quranSettings.previouslyDownloadedPageTypes
    val currentPagesToDownload = quranDataStatus!!.portraitWidth + "_" +
        quranDataStatus!!.landscapeWidth
    val arePageSetsEquivalent = lastDownloadedPages == currentPagesToDownload

    // check for the existence of a .q4a file in the base directory to distinguish
    // the case in which the whole directory was wiped (nothing we can really do here?)
    // and the case when just the images disappeared.
    var didHiddenFileSurvive = false
    val baseDirectory = quranFileUtils.quranBaseDirectory
    if (baseDirectory != null) {
      try {
        didHiddenFileSurvive = File(baseDirectory, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).exists()
      } catch (e: Exception) {
        Timber.e(e)
      }
    }

    // check for the existence of a q4a file in the base directory - this is the same as
    // above, but is there to see if, perhaps, hidden files survive but non-hidden ones don't.
    var didNormalFileSurvive = false
    if (baseDirectory != null) {
      try {
        didNormalFileSurvive = File(baseDirectory, QURAN_DIRECTORY_MARKER_FILE).exists()
      } catch (e: Exception) {
        Timber.e(e)
      }
    }

    // check for the existence of a .q4a file in the internal no_backup directory.
    var didInternalFileSurvive = false
    try {
      didInternalFileSurvive = File(noBackupFilesDir, QURAN_HIDDEN_DIRECTORY_MARKER_FILE).exists()
    } catch (e: Exception) {
      Timber.e(e)
    }

    // how recently did the files disappear?
    val downloadTime = quranSettings.previouslyDownloadedTime
    val recencyOfRemoval: String = if (downloadTime == 0L) {
      "no timestamp"
    } else {
      val deltaInSeconds = (System.currentTimeMillis() - downloadTime) / 1000
      when {
        deltaInSeconds < 5 * 60 -> { "within 5 minutes" }
        deltaInSeconds < 10 * 60 -> { "within 10 minutes" }
        deltaInSeconds < 60 * 60 -> { "within an hour" }
        deltaInSeconds < 24 * 60 * 60 -> { "within a day" }
        else -> { "more than a day" }
      }
    }

    // log an exception
    Timber.w(quranDataStatus.toString())
    Timber.w(quranDataPresenter.getDebugLog())
    Timber.w("appLocation: %s", appLocation)
    Timber.w("sdcard: %s, app dir: %s", sdcard, appDir)
    Timber.w("didNormalFileSurvive: %s", didNormalFileSurvive)
    Timber.w("didInternalFileSurvive: %s", didInternalFileSurvive)
    Timber.w("didHiddenFileSurvive: %s", didHiddenFileSurvive)
    Timber.w(
        "seconds passed: %d, recency: %s",
        (System.currentTimeMillis() - downloadTime) / 1000, recencyOfRemoval
    )
    Timber.w("isPagePathTheSame: %s", isPagePathTheSame)
    Timber.w("arePagesToDownloadTheSame: %s", arePageSetsEquivalent)
    Timber.e(IllegalStateException("Deleted Data"), "Unable to Download Pages")

    // throw and log another exception if we have "com.quran" in appLocation but don't
    // can't map it back eo either filesDir or any of the externalFilesDirs - this would
    // suggest that one of these actually moved (which is possible according to the docs).
    // doing this to gauge whether this is really an edge case as i suspect it is, or a
    // real problem i should worry about and handle.
    if ("com.quran" in appLocation) {
      val internalDir = filesDir.absolutePath
      val isInternal = appLocation == internalDir

      val externalDirs = ContextCompat.getExternalFilesDirs(this, null)
      val isExternal = !isInternal && externalDirs.any { it.absolutePath == appLocation }
      if (!isInternal && !isExternal) {
        Timber.w("appLocation: %s", appLocation)
        Timber.w("internal: %s", internalDir)
        externalDirs.forEach {
          Timber.w("external: %s", it)
        }
        Timber.e(IllegalStateException("data deleted from unknown directory"))
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
    val destination = quranFileUtils.getQuranImagesBaseDirectory(this@QuranDataActivity)

    // start service
    val intent = ServiceIntentHelper.getDownloadIntent(
        this, url,
        destination, getString(R.string.app_name), PAGES_DOWNLOAD_KEY,
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
    val dataStatus = quranDataStatus
    var message = R.string.downloadPrompt
    if (quranScreenInfo.isDualPageMode && dataStatus!!.needLandscape()) {
      message = R.string.downloadTabletPrompt
    }
    if (!TextUtils.isEmpty(dataStatus!!.patchParam)) {
      // patch message if applicable
      message = R.string.downloadImportantPrompt
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
    private const val REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1
    private const val REQUEST_POST_NOTIFICATION_PERMISSIONS = 2
    private const val QURAN_DIRECTORY_MARKER_FILE = "q4a"
    private const val QURAN_HIDDEN_DIRECTORY_MARKER_FILE = ".q4a"
  }
}
