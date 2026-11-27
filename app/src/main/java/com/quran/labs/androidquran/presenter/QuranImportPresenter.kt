package com.quran.labs.androidquran.presenter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.BookmarkData
import com.quran.labs.androidquran.QuranImportActivity
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.service.util.PermissionUtil.canRequestWriteExternalStoragePermission
import com.quran.labs.androidquran.service.util.PermissionUtil.haveWriteExternalStoragePermission
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.observers.DisposableMaybeObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

@SingleIn(AppScope::class)
class QuranImportPresenter @Inject internal constructor(
  @param:ApplicationContext private val appContext: Context,
  private val bookmarkImportExportModel: BookmarkImportExportModel,
  private val contentResolverOps: ContentResolverOps
) : Presenter<QuranImportActivity> {
  private val compositeDisposable = CompositeDisposable()

  private var requestingPermissions = false
  private var importObservable: Observable<Boolean>? = null
  private var currentActivity: QuranImportActivity? = null

  private fun handleIntent(intent: Intent) {
    requestingPermissions = false
    if (importObservable == null) {
      val uri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      if (uri != null) {
        parseIntentUri(uri)
      } else {
        currentActivity?.showError()
      }
    } else {
      subscribeToImportData()
    }
  }

  fun importData(data: BookmarkData) {
    importObservable = bookmarkImportExportModel.importBookmarksObservable(data)
    subscribeToImportData()
  }

  fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
    if (requestCode == REQUEST_WRITE_TO_SDCARD_PERMISSIONS) {
      requestingPermissions = false
      val currentActivity = currentActivity
      if (currentActivity != null) {
        if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          handleIntent(currentActivity.intent)
        } else {
          currentActivity.showPermissionsError()
        }
      }
    }
  }

  private fun subscribeToImportData() {
    val observable = importObservable
    if (observable != null) {
      compositeDisposable.add(
        observable
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe { ignored: Boolean? ->
            currentActivity?.showImportComplete()
            importObservable = null
          })
    }
  }

  private fun parseIntentUri(uri: Uri) {
    getBookmarkDataObservable(parseUri(uri))
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(object : DisposableMaybeObserver<BookmarkData>() {
        override fun onSuccess(bookmarkData: BookmarkData) {
          currentActivity?.showImportConfirmationDialog(bookmarkData)
        }

        override fun onError(e: Throwable) {
          if (currentActivity != null) {
            if (e is SecurityException) {
              currentActivity?.showError()
            } else {
              handleExternalStorageFile(uri)
            }
          }
        }

        override fun onComplete() {
          if (currentActivity != null) {
            handleExternalStorageFile(uri)
          }
        }
      })
  }

  private fun handleExternalStorageFile(uri: Uri) {
    val currentActivity = currentActivity
    if (haveWriteExternalStoragePermission(appContext)) {
      handleExternalStorageFileInternal(uri)
    } else if (currentActivity != null) {
      requestingPermissions = true
      if (canRequestWriteExternalStoragePermission(currentActivity)) {
        QuranSettings.getInstance(appContext).setSdcardPermissionsDialogPresented()
        ActivityCompat.requestPermissions(
          currentActivity,
          arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
          REQUEST_WRITE_TO_SDCARD_PERMISSIONS
        )
      } else {
        currentActivity.showPermissionsError()
      }
    }
  }

  private fun handleExternalStorageFileInternal(uri: Uri) {
    getBookmarkDataObservable(parseExternalFile(uri))
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(object : DisposableMaybeObserver<BookmarkData>() {
        override fun onSuccess(bookmarkData: BookmarkData) {
          currentActivity?.showImportConfirmationDialog(bookmarkData)
        }

        override fun onError(e: Throwable) {
          currentActivity?.showError()
        }

        override fun onComplete() {
          currentActivity?.showError()
        }
      })
  }

  private fun getBookmarkDataObservable(source: Maybe<BufferedSource>): Maybe<BookmarkData> {
    return source
      .flatMap { bufferedSource: BufferedSource ->
        bookmarkImportExportModel.readBookmarks(bufferedSource).toMaybe()
      }
      .subscribeOn(Schedulers.io())
  }

  @VisibleForTesting
  fun parseUri(uri: Uri): Maybe<BufferedSource> {
    return Maybe.defer {
      if (isBlockedUri(uri)) {
        return@defer Maybe.error(SecurityException("Blocked URI"))
      }
      val pfd = contentResolverOps.openFileDescriptor(uri, "r")
      if (pfd != null) {
        return@defer Maybe.just(
          ParcelFileDescriptor.AutoCloseInputStream(pfd).source().buffer()
        )
      }
      Maybe.empty()
    }
  }

  @VisibleForTesting
  fun parseExternalFile(uri: Uri): Maybe<BufferedSource> {
    return Maybe.defer {
      if (isBlockedUri(uri)) {
        return@defer Maybe.error(SecurityException("Blocked URI"))
      }
      val stream = contentResolverOps.openInputStream(uri)
      if (stream != null) {
        return@defer Maybe.just(
          stream.source().buffer()
        )
      }
      Maybe.empty()
    }
  }

  private fun isBlockedUri(uri: Uri): Boolean {
    if (!"file".equals(uri.scheme, ignoreCase = true)) {
      return false
    }
    val rawPath = uri.path
    if (rawPath.isNullOrBlank()) {
      return true
    }
    val canonicalPath = try {
      File(rawPath).canonicalPath
    } catch (_: IOException) {
      return true
    }

    val normalizedCanonical = canonicalPath.replace('\\', '/').substringAfter(':')
    val blockedPrefixes = listOf("/dev", "/proc", "/sys")
    if (blockedPrefixes.any { prefix ->
        rawPath == prefix || rawPath.startsWith("$prefix/") ||
          normalizedCanonical == prefix || normalizedCanonical.startsWith("$prefix/")
      }
    ) {
      return true
    }

    val privateDirs = listOfNotNull(
      ContextCompat.getDataDir(appContext),
      appContext.filesDir,
      appContext.cacheDir,
      appContext.codeCacheDir,
      appContext.noBackupFilesDir
    )
    return privateDirs.any { dir ->
      val dirCanonical = try {
        dir.canonicalPath
      } catch (_: IOException) {
        return@any true
      }
      val dirPrefix = if (dirCanonical.endsWith(File.separator)) {
        dirCanonical
      } else {
        dirCanonical + File.separator
      }
      canonicalPath == dirCanonical || canonicalPath.startsWith(dirPrefix)
    }
  }

  override fun bind(what: QuranImportActivity) {
    currentActivity = what
    if (!what.isShowingDialog() && !requestingPermissions) {
      handleIntent(what.intent)
    }
  }

  override fun unbind(what: QuranImportActivity) {
    if (what == currentActivity) {
      currentActivity = null
      compositeDisposable.clear()
    }
  }

  companion object {
    private const val REQUEST_WRITE_TO_SDCARD_PERMISSIONS = 1
  }
}
