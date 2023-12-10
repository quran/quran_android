package com.quran.labs.androidquran.presenter.translation

import android.content.Context
import android.util.Pair
import androidx.annotation.VisibleForTesting
import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationList
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.DatabaseHandler.Companion.getDatabaseHandler
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.di.qualifier.ApplicationContext
import com.squareup.moshi.Moshi
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.functions.Supplier
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class TranslationManagerPresenter @Inject internal constructor(
  @param:ApplicationContext private val appContext: Context,
  private val okHttpClient: OkHttpClient,
  private val quranSettings: QuranSettings,
  private val translationsDBAdapter: TranslationsDBAdapter,
  private val quranFileUtils: QuranFileUtils
) : Presenter<TranslationManagerActivity> {
  @VisibleForTesting
  var host: String = Constants.HOST

  private var currentActivity: TranslationManagerActivity? = null

  fun checkForUpdates() {
    getTranslationsList(true)
  }

  fun getTranslationsList(forceDownload: Boolean) {
    val isCacheStale = System.currentTimeMillis() -
        quranSettings.lastUpdatedTranslationDate > Constants.MIN_TRANSLATION_REFRESH_TIME
    val source: Observable<TranslationList> = Observable.concat(
      cachedTranslationListObservable, remoteTranslationListObservable
    )
    val observableSource: Observable<TranslationList> = if (forceDownload) {
      // we only force if we pulled to refresh or are refreshing in the background,
      // implying that we have data on the screen already (or don't need data in the
      // background case), so just get remote data.
      remoteTranslationListObservable
    } else if (isCacheStale) {
      source
    } else {
      source.take(1)
    }
    observableSource
      .filter { translationList: TranslationList -> translationList.translations.isNotEmpty() }
      .map { translationList: TranslationList ->
        mergeWithServerTranslations(translationList.translations)
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(object : DisposableObserver<List<TranslationItem>>() {
        override fun onNext(translationItems: List<TranslationItem>) {
          currentActivity?.onTranslationsUpdated(translationItems)

          // used for marking upgrades, irrespective of whether or not there is a bound activity
          var updatedTranslations = false
          for (item in translationItems) {
            if (item.needsUpgrade()) {
              updatedTranslations = true
              break
            }
          }
          quranSettings.setHaveUpdatedTranslations(updatedTranslations)
        }

        override fun onError(e: Throwable) {
          if (e !is IOException) {
            Timber.e(e, "error updating translations list")
          }
          currentActivity?.onErrorDownloadTranslations()
        }

        override fun onComplete() {}
      })
  }

  fun updateItem(item: TranslationItem) {
    Observable.fromCallable {

      // for upgrades, remove the old file to stop the tafseer from showing up
      // twice. this happens because old and new tafaseer (ex ibn kathir) have
      // different ids when they target different schema versions, and so the
      // old file needs to be removed from the database explicitly
      val (_, minimumVersion, _, _, _, fileName) = item.translation
      if (minimumVersion >= 5) {
        translationsDBAdapter.legacyDeleteTranslationByFileName(fileName)
      }
      translationsDBAdapter.legacyWriteTranslationUpdates(listOf(item))
    }.subscribeOn(Schedulers.io())
      .subscribe()
  }

  fun updateItemOrdering(items: List<TranslationItem>) {
    Observable.fromCallable { translationsDBAdapter.legacyWriteTranslationUpdates(items) }
      .subscribeOn(Schedulers.io())
      .subscribe()
  }

  val cachedTranslationListObservable: Observable<TranslationList>
    get() = Observable.defer(Supplier<ObservableSource<out TranslationList>> {
      try {
        val cachedFile = cachedFile
        if (cachedFile.exists()) {
          val moshi = Moshi.Builder().build()
          val jsonAdapter = moshi.adapter(
            TranslationList::class.java
          )
          val list = jsonAdapter.fromJson(cachedFile.source().buffer())
          if (list != null) {
            return@Supplier Observable.just<TranslationList>(list)
          }
        }
      } catch (e: Exception) {
        Timber.e(e)
      }
      Observable.empty()
    })
  val remoteTranslationListObservable: Observable<TranslationList>
    get() {
      val url = host + WEB_SERVICE_ENDPOINT
      return downloadTranslationList(url)
        .onErrorResumeWith(downloadTranslationList(url))
        .doOnNext { translationList: TranslationList ->
          translationList.translations
          if (translationList.translations.isNotEmpty()) {
            writeTranslationList(translationList)
          }
        }
    }

  private fun downloadTranslationList(url: String): Observable<TranslationList> {
    return Observable.fromCallable(Callable<TranslationList> {
      val request: Request = Request.Builder()
        .url(url)
        .build()
      val response = okHttpClient.newCall(request).execute()
      val moshi = Moshi.Builder().build()
      val jsonAdapter = moshi.adapter(
        TranslationList::class.java
      )
      val responseBody = response.body
      val result = jsonAdapter.fromJson(responseBody!!.source())
      responseBody.close()
      result
    })
  }

  open fun writeTranslationList(list: TranslationList) {
    val cacheFile = cachedFile
    try {
      val directory = cacheFile.getParentFile()
      val directoryExists = directory.mkdirs() || directory.isDirectory()
      if (directoryExists) {
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(
          TranslationList::class.java
        )
        val sink = cacheFile.sink().buffer()
        jsonAdapter.toJson(sink, list)
        sink.close()
        quranSettings.lastUpdatedTranslationDate = System.currentTimeMillis()
      }
    } catch (e: Exception) {
      cacheFile.delete()
      Timber.e(e)
    }
  }

  private val cachedFile: File
    get() {
      val dir = quranFileUtils.getQuranDatabaseDirectory(appContext)
      return File(dir + File.separator + CACHED_RESPONSE_FILE_NAME)
    }

  private fun mergeWithServerTranslations(serverTranslations: List<Translation>): List<TranslationItem> {
    val results: MutableList<TranslationItem> = ArrayList(serverTranslations.size)
    val localTranslations = translationsDBAdapter.getTranslationsHash()
    val databaseDir = quranFileUtils.getQuranDatabaseDirectory(appContext)
    val updates: MutableList<TranslationItem> = ArrayList()
    var i = 0
    val count = serverTranslations.size
    while (i < count) {
      val translation = serverTranslations[i]
      val local = localTranslations[translation.id]
      val dbFile = File(databaseDir, translation.fileName)
      var exists = dbFile.exists()
      var item: TranslationItem
      var override: TranslationItem? = null
      if (exists) {
        if (local == null) {
          // text version, schema version
          val versions = getVersionFromDatabase(translation.fileName)
          item = TranslationItem(translation, versions.first)
          if (versions.second != translation.minimumVersion) {
            // schema change, write downloaded schema version to the db and return server item
            override =
              TranslationItem(translation.withSchema(versions.second), versions.first)
          }
        } else {
          item = TranslationItem(translation, local.version, local.displayOrder)
        }
      } else {
        item = TranslationItem(translation)
      }
      if (exists && !item.exists()) {
        // delete the file, it has been corrupted
        if (dbFile.delete()) {
          exists = false
        }
      }
      if (local == null && exists || local != null && !exists) {
        if (override != null && item.translation.minimumVersion >= 5) {
          // certain schema changes, especially those going to v5, keep the same filename while
          // changing the database entry id. this could cause duplicate entries in the database.
          // work around it by removing the existing entries before doing the updates.
          translationsDBAdapter.legacyDeleteTranslationByFileName(override.translation.fileName)
        }
        updates.add(override ?: item)
      } else if (local != null && local.languageCode == null) {
        // older items don't have a language code
        updates.add(item)
      }
      results.add(item)
      i++
    }
    if (!updates.isEmpty()) {
      translationsDBAdapter.legacyWriteTranslationUpdates(updates)
    }
    return results
  }

  private fun getVersionFromDatabase(filename: String): Pair<Int, Int> {
    try {
      val handler = getDatabaseHandler(appContext, filename, quranFileUtils)
      if (handler.validDatabase()) {
        return Pair(handler.getTextVersion(), handler.getSchemaVersion())
      }
    } catch (e: Exception) {
      Timber.d(e, "exception opening database: %s", filename)
    }
    return Pair(0, 0)
  }

  override fun bind(what: TranslationManagerActivity) {
    currentActivity = what
  }

  override fun unbind(what: TranslationManagerActivity) {
    if (what === currentActivity) {
      currentActivity = null
    }
  }

  companion object {
    private const val WEB_SERVICE_ENDPOINT = "data/translations.php?v=5"
    private const val CACHED_RESPONSE_FILE_NAME = "translations.v5.cache"
  }
}
