package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.coroutineScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranImportActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.service.util.PermissionUtil.haveWriteExternalStoragePermission
import com.quran.labs.androidquran.ui.preference.DataListPreference
import com.quran.labs.androidquran.ui.util.ToastCompat.makeText
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.StorageUtils
import com.quran.labs.androidquran.util.StorageUtils.getAllStorageLocations
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableMaybeObserver
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class QuranAdvancedSettingsFragment : PreferenceFragmentCompat() {
  private lateinit var listStoragePref: DataListPreference
  private lateinit var storageList: List<StorageUtils.Storage>
  private lateinit var appContext: Context

  private var appSize = 0
  private var isPaused = false
  private var internalSdcardLocation: String? = null
  private var dialog: Dialog? = null
  private var exportSubscription: Disposable? = null
  private var logsSubscription: Disposable? = null

  @Inject
  lateinit var bookmarkImportExportModel: BookmarkImportExportModel

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var quranScreenInfo: QuranScreenInfo

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.quran_advanced_preferences)
    val context: Context = requireActivity()
    appContext = context.applicationContext

    // field injection
    (appContext as QuranApplication).applicationComponent.inject(this)

    val logsPref = findPreference<Preference>(Constants.PREF_LOGS)
    if (BuildConfig.DEBUG || "beta" == BuildConfig.BUILD_TYPE) {
      logsPref!!.onPreferenceClickListener =
        Preference.OnPreferenceClickListener {
          if (logsSubscription == null) {
            logsSubscription = Observable.fromIterable(Timber.forest())
              .filter { tree: Timber.Tree? -> tree is RecordingLogTree }
              .firstElement()
              .map { tree: Timber.Tree -> (tree as RecordingLogTree).getLogs() }
              .map { logs: String ->
                "${QuranUtils.getDebugInfo(appContext, quranScreenInfo)}\n\n$logs"
              }
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeWith(object : DisposableMaybeObserver<String>() {
                override fun onSuccess(logs: String) {
                  val intent = Intent(Intent.ACTION_SEND)
                  intent.type = "message/rfc822"
                  intent.putExtra(
                    Intent.EXTRA_EMAIL,
                    arrayOf(appContext.getString(R.string.logs_email))
                  )
                  intent.putExtra(Intent.EXTRA_TEXT, logs)
                  intent.putExtra(Intent.EXTRA_SUBJECT, "Logs")
                  startActivity(
                    Intent.createChooser(
                      intent,
                      appContext.getString(R.string.prefs_send_logs_title)
                    )
                  )
                  logsSubscription = null
                }

                override fun onError(e: Throwable) {}
                override fun onComplete() {}
              })
          }
          true
        }
    } else {
      removeAdvancePreference(logsPref)
    }
    val importPref = findPreference<Preference>(Constants.PREF_IMPORT)
    importPref?.onPreferenceClickListener =
      Preference.OnPreferenceClickListener {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        val mimeTypes = arrayOf("application/*", "text/*")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
        true
      }

    val exportPref = findPreference<Preference>(Constants.PREF_EXPORT)
    exportPref?.onPreferenceClickListener =
      Preference.OnPreferenceClickListener {
        if (exportSubscription == null) {
          exportSubscription = bookmarkImportExportModel.exportBookmarksObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(object : DisposableSingleObserver<Uri>() {
              override fun onSuccess(uri: Uri) {
                onBookmarkExportSuccess(uri, context)
                exportSubscription = null
              }

              override fun onError(e: Throwable) {
                exportSubscription = null
                onExportBookmarksError(context)
              }
            })
        }
        true
      }

    val exportCSVPref = findPreference<Preference>(Constants.PREF_EXPORT_CSV)
    exportCSVPref?.onPreferenceClickListener =
      Preference.OnPreferenceClickListener {
        if (exportSubscription == null) {
          exportSubscription = bookmarkImportExportModel.exportBookmarksCSVObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(object : DisposableSingleObserver<Uri>() {
              override fun onSuccess(uri: Uri) {
                onBookmarkExportSuccess(uri, context)
                exportSubscription = null
              }

              override fun onError(e: Throwable) {
                exportSubscription = null
                onExportBookmarksError(context)
              }
            })
        }
        true
      }

    internalSdcardLocation = Environment.getExternalStorageDirectory().absolutePath
    listStoragePref = findPreference(getString(R.string.prefs_app_location))!!
    listStoragePref.isEnabled = false
    storageList = try {
      getAllStorageLocations(context.applicationContext)
    } catch (e: Exception) {
      Timber.d(e, "Exception while trying to get storage locations")
      emptyList()
    }

    // Hide app location pref if there is no storage option
    // except for the normal Environment.getExternalStorageDirectory
    if (storageList.size <= 1) {
      Timber.d("removing advanced settings from preferences")
      hideStorageListPref()
    } else {
      lifecycle.coroutineScope.launch {
        listStoragePref.setSummary(R.string.prefs_calculating_app_size)
        appSize = withContext(Dispatchers.IO) {
          quranFileUtils.getAppUsedSpace(appContext)
        }

        if (!isPaused) {
          loadStorageOptions(appContext)
          listStoragePref.setSummary(R.string.prefs_app_location_summary)
        }
      }
    }
  }

  private fun onBookmarkExportSuccess(uri: Uri, context: Context) {
    val shareIntent = createShareIntent(uri)
    val intents = appContext.packageManager
      .queryIntentActivities(shareIntent, 0)
    if (intents.size > 1) {
      // if only one, then that is likely Quran for Android itself, so don't show
      // the chooser since it doesn't really make sense.
      context.startActivity(
        Intent.createChooser(
          shareIntent,
          context.getString(R.string.prefs_export_title)
        )
      )
    } else {
      val exportedPath = File(appContext.getExternalFilesDir(null), "backups")
      val exported = appContext.getString(
        R.string.exported_data, exportedPath.toString()
      )
      makeText(appContext, exported, Toast.LENGTH_LONG).show()
    }
  }

  private fun onExportBookmarksError(context: Context) {
    if (isAdded) {
      makeText(context, R.string.export_data_error, Toast.LENGTH_LONG).show()
    }
  }

  private fun createShareIntent(uri: Uri): Intent {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
    shareIntent.setDataAndType(uri, "application/json")
    return shareIntent
  }

  override fun onDestroy() {
    exportSubscription?.dispose()
    logsSubscription?.dispose()
    dialog?.dismiss()
    super.onDestroy()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
      val activity: Activity? = activity
      if (activity != null) {
        val intent = Intent(activity, QuranImportActivity::class.java)
        intent.data = data!!.data
        startActivity(intent)
      }
    }
  }

  private fun removeAdvancePreference(preference: Preference?) {
    // these null checks are to fix a crash due to an NPE on 4.4.4
    if (preference != null) {
      val group = findPreference<PreferenceGroup>(Constants.PREF_ADVANCED_QURAN_SETTINGS)
      group?.removePreference(preference)
    }
  }

  private fun hideStorageListPref() {
    removeAdvancePreference(listStoragePref)
  }

  private fun loadStorageOptions(context: Context) {
    try {
      if (appSize == -1) {
        // sdcard is not mounted...
        hideStorageListPref()
        return
      }
      listStoragePref.setLabelsAndSummaries(context, appSize, storageList)
      val storageMap = HashMap<String, StorageUtils.Storage>(
        storageList.size
      )
      for (storage in storageList) {
        storageMap[storage.mountPoint] = storage
      }
      listStoragePref
        .setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
          val context1: Context = requireActivity()
          val settings = QuranSettings.getInstance(context1)
          if (TextUtils.isEmpty(settings.appCustomLocation) && Environment.getExternalStorageDirectory() == newValue) {
            // do nothing since we're moving from empty settings to
            // the default sdcard setting, which are the same, but write it.
            return@setOnPreferenceChangeListener false
          }

          // this is called right before the preference is saved
          val newLocation = newValue as String
          val destStorage = storageMap[newLocation]
          val current = settings.appCustomLocation
          if (appSize < destStorage!!.getFreeSpace()) {
            if (current == null || current != newLocation) {
              handleMove(newLocation, destStorage)
            }
          } else {
            makeText(
              context1,
              getString(
                R.string.prefs_no_enough_space_to_move_files
              ),
              Toast.LENGTH_LONG
            ).show()
          }
          false
        }
      listStoragePref.isEnabled = true
    } catch (e: Exception) {
      Timber.e(e, "error loading storage options")
      hideStorageListPref()
    }
  }

  private fun requestExternalStoragePermission(newLocation: String) {
    val activity: Activity? = activity
    if (activity is QuranAdvancedPreferenceActivity) {
      activity
        .requestWriteExternalSdcardPermission(newLocation)
    }
  }

  private fun handleMove(newLocation: String, storageLocation: StorageUtils.Storage?) {
    if (newLocation == internalSdcardLocation) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // on Android Q (not really "above" since we don't show the option above),
        // warn if the person tries to use the /sdcard path.
        showScopedStorageConfirmation(newLocation, storageLocation)
      } else {
        // otherwise just copy
        moveFiles(newLocation, storageLocation)
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // don't warn for using Android app directories on Q and above
      moveFiles(newLocation, storageLocation)
    } else {
      // but on older versions, warn
      showKitKatConfirmation(newLocation, storageLocation)
    }
  }

  private fun showScopedStorageConfirmation(
    newLocation: String,
    storageLocation: StorageUtils.Storage?
  ) {
    showConfirmation(newLocation, storageLocation, R.string.scoped_storage_message)
  }

  private fun showKitKatConfirmation(
    newLocation: String,
    storageLocation: StorageUtils.Storage?
  ) {
    showConfirmation(newLocation, storageLocation, R.string.kitkat_external_message)
  }

  private fun showConfirmation(
    newLocation: String,
    storageLocation: StorageUtils.Storage?,
    @StringRes message: Int
  ) {
    val context: Context? = activity
    if (context != null) {
      val b = AlertDialog.Builder(context)
        .setTitle(R.string.warning)
        .setMessage(message)
        .setPositiveButton(R.string.dialog_ok) { currentDialog: DialogInterface, _: Int ->
          moveFiles(newLocation, storageLocation)
          currentDialog.dismiss()
          dialog = null
        }
        .setNegativeButton(com.quran.mobile.common.ui.core.R.string.cancel) { currentDialog: DialogInterface, _: Int ->
          currentDialog.dismiss()
          dialog = null
        }
      val dialog = b.create()
      dialog.show()
      this.dialog = dialog
    }
  }

  private fun moveFiles(newLocation: String, storageLocation: StorageUtils.Storage?) {
    val context = context
    if (context != null) {
      if (storageLocation!!.doesRequirePermission() &&
        !haveWriteExternalStoragePermission(context)
      ) {
        requestExternalStoragePermission(newLocation)
      } else {
        moveFiles(newLocation)
      }
    }
  }

  fun moveFiles(newLocation: String) {
    val context = context
    if (context != null) {
      lifecycle.coroutineScope.launch {
        val progressDialog: ProgressDialog = ProgressDialog(activity).apply {
          setMessage(appContext.getString(R.string.prefs_copying_app_files))
          setCancelable(false)
        }
        progressDialog.show()
        dialog = progressDialog

        val result = withContext(Dispatchers.IO) {
          quranFileUtils.moveAppFiles(appContext, newLocation)
        }

        if (!isPaused) {
          dialog?.dismiss()
          if (result) {
            val quranSettings = QuranSettings.getInstance(appContext)
            quranSettings.appCustomLocation = newLocation
            quranSettings.removeDidDownloadPages()
            listStoragePref.value = newLocation
          } else {
            makeText(
              appContext,
              getString(R.string.prefs_err_moving_app_files),
              Toast.LENGTH_LONG
            ).show()
          }
          dialog = null
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    isPaused = false
  }

  override fun onPause() {
    isPaused = true
    super.onPause()
  }

  companion object {
    private const val REQUEST_CODE_IMPORT = 1
  }
}
