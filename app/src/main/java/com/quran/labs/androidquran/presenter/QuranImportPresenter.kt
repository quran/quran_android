package com.quran.labs.androidquran.presenter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import com.quran.data.model.bookmark.BookmarkData
import com.quran.labs.androidquran.QuranImportActivity
import com.quran.labs.androidquran.model.bookmark.BookmarkImportExportModel
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.service.util.PermissionUtil.canRequestWriteExternalStoragePermission
import com.quran.labs.androidquran.service.util.PermissionUtil.haveWriteExternalStoragePermission
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.qualifier.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.observers.DisposableMaybeObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranImportPresenter @Inject internal constructor(
  @ApplicationContext private val appContext: Context,
  private val bookmarkImportExportModel: BookmarkImportExportModel,
  private val bookmarkModel: BookmarkModel
) : Presenter<QuranImportActivity> {
  private val compositeDisposable = CompositeDisposable()

  private var requestingPermissions = false
  private var importObservable: Observable<Boolean>? = null
  private var currentActivity: QuranImportActivity? = null

  private fun handleIntent(intent: Intent) {
    requestingPermissions = false
    if (importObservable == null) {
      val uri = intent.data ?: intent.extras?.get(Intent.EXTRA_STREAM) as Uri?
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
    importObservable = bookmarkModel.importBookmarksObservable(data)
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
            handleExternalStorageFile(uri)
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
      val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
      if (pfd != null) {
        val fd = pfd.fileDescriptor
        return@defer Maybe.just<BufferedSource>(
          FileInputStream(
            fd
          ).source().buffer()
        )
      }
      Maybe.empty()
    }
  }

  @VisibleForTesting
  fun parseExternalFile(uri: Uri): Maybe<BufferedSource> {
    return Maybe.defer {
      val stream = appContext.contentResolver.openInputStream(uri)
      if (stream != null) {
        return@defer Maybe.just<BufferedSource>(
          stream.source().buffer()
        )
      }
      Maybe.empty()
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
