package com.quran.labs.androidquran.ui

import android.content.DialogInterface
import android.content.IntentFilter
import android.os.Bundle
import android.util.SparseIntArray
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.translation.TranslationHeader
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationRowData
import com.quran.labs.androidquran.database.DatabaseHandler.Companion.clearDatabaseHandlerIfExists
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.ServiceIntentHelper.getDownloadIntent
import com.quran.labs.androidquran.ui.adapter.DownloadedItemActionListener
import com.quran.labs.androidquran.ui.adapter.DownloadedMenuActionListener
import com.quran.labs.androidquran.ui.adapter.TranslationsAdapter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.max

class TranslationManagerActivity : AppCompatActivity(), SimpleDownloadListener,
  DownloadedMenuActionListener {
  private var allItems: List<TranslationItem> = emptyList()
  private var currentSortedDownloads: List<TranslationItem> = emptyList()
  private var originalSortedDownloads: List<TranslationItem> = emptyList()
  private var translationPositions: SparseIntArray = SparseIntArray()
  private var downloadingItem: TranslationItem? = null
  private var databaseDirectory: String? = null
  private var downloadReceiver: DefaultDownloadReceiver? = null
  private var actionMode: ActionMode? = null
  private var downloadedItemActionListener: DownloadedItemActionListener? = null

  @Inject
  lateinit var presenter: TranslationManagerPresenter

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  @Inject
  lateinit var quranSettings: QuranSettings

  private lateinit var adapter: TranslationsAdapter
  private lateinit var selectionListener: TranslationSelectionListener
  private lateinit var onClickDownloadDisposable: Disposable
  private lateinit var onClickRemoveDisposable: Disposable
  private lateinit var onClickRankUpDisposable: Disposable
  private lateinit var onClickRankDownDisposable: Disposable

  private lateinit var translationSwipeRefresh: SwipeRefreshLayout
  private lateinit var translationRecycler: RecyclerView

  private val scope = MainScope()

  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    setContentView(R.layout.translation_manager)
    translationSwipeRefresh = findViewById(R.id.translation_swipe_refresh)
    translationRecycler = findViewById(R.id.translation_recycler)
    val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
    translationRecycler.setLayoutManager(layoutManager)
    adapter = TranslationsAdapter(this)
    translationRecycler.setAdapter(adapter)
    selectionListener = TranslationSelectionListener(adapter)
    databaseDirectory = quranFileUtils.getQuranDatabaseDirectory(this)
    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true)
      actionBar.setTitle(R.string.prefs_translations)
    }
    onClickDownloadDisposable = adapter.getOnClickDownloadSubject()
      .subscribe { translationRowData: TranslationRowData -> downloadItem(translationRowData) }
    onClickRemoveDisposable = adapter.getOnClickRemoveSubject()
      .subscribe { translationRowData: TranslationRowData -> removeItem(translationRowData) }
    onClickRankUpDisposable = adapter.getOnClickRankUpSubject()
      .subscribe { targetRow: TranslationRowData -> rankUpItem(targetRow) }
    onClickRankDownDisposable = adapter.getOnClickRankDownSubject()
      .subscribe { targetRow: TranslationRowData -> rankDownItem(targetRow) }
    translationSwipeRefresh.setOnRefreshListener { onRefresh() }
    translationSwipeRefresh.isRefreshing = true
    refreshTranslations()
  }

  public override fun onStop() {
    val receiver = downloadReceiver
    if (receiver != null) {
      receiver.setListener(null)
      LocalBroadcastManager.getInstance(this)
        .unregisterReceiver(receiver)
      downloadReceiver = null
    }
    super.onStop()
  }

  override fun onDestroy() {
    scope.cancel()
    onClickDownloadDisposable.dispose()
    onClickRemoveDisposable.dispose()
    onClickRankUpDisposable.dispose()
    onClickRankDownDisposable.dispose()
    super.onDestroy()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == android.R.id.home) {
      finish()
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  override fun handleDownloadSuccess() {
    val downloadingItem = downloadingItem
    if (downloadingItem != null) {
      if (downloadingItem.exists()) {
        try {
          val f = File(
            databaseDirectory,
            downloadingItem.translation.fileName + UPGRADING_EXTENSION
          )
          if (f.exists()) {
            f.delete()
          }
        } catch (e: Exception) {
          Timber.d(e, "error removing old database file")
        }
      }

      // TODO: we can avoid the cost of sorting once we can listen to db updates
      // in which case we'd set the local version as -1 so it gets properly assigned after.
      val sortedItems = sortedDownloadedItems()
      val lastDisplayOrder =
        if (sortedItems.isEmpty()) 0 else sortedItems[sortedItems.size - 1].displayOrder
      val (_, _, currentVersion) = downloadingItem.translation
      updateTranslationItem(
        downloadingItem.withLocalVersionAndDisplayOrder(
          currentVersion, lastDisplayOrder + 1
        )
      )

      // update active translations and add this item to it
      val settings = QuranSettings.getInstance(this)
      val activeTranslations = settings.activeTranslations
      activeTranslations.add(downloadingItem.translation.fileName)
      settings.activeTranslations = activeTranslations
    }
    this.downloadingItem = null
    generateListItems()
  }

  override fun handleDownloadFailure(errId: Int) {
    val downloadingItem = downloadingItem
    if (downloadingItem != null && downloadingItem.exists()) {
      try {
        val f = File(
          databaseDirectory,
          downloadingItem.translation.fileName + UPGRADING_EXTENSION
        )
        val destFile = File(databaseDirectory, downloadingItem.translation.fileName)
        if (f.exists() && !destFile.exists()) {
          f.renameTo(destFile)
        } else {
          f.delete()
        }
      } catch (e: Exception) {
        Timber.d(e, "error restoring translation after failed download")
      }
    }
    this.downloadingItem = null
  }

  private fun onRefresh() {
    refreshTranslations(true)
  }

  private fun refreshTranslations(forceDownload: Boolean = false) {
    presenter.getTranslations(forceDownload)
      .onEach { onTranslationsUpdated(it) }
      .catch { onErrorDownloadTranslations() }
      .launchIn(scope)
  }

  private fun updateTranslationItem(updated: TranslationItem) {
    val id = updated.translation.id
    val allItemsIndex = translationPositions[id]
    if (allItems.size > allItemsIndex) {
      allItems = allItems.toMutableList().apply {
        removeAt(allItemsIndex)
        add(allItemsIndex, updated)
      }
    }
    scope.launch {
      presenter.updateItem(updated)
    }
  }

  private fun updateDownloadedItems() {
    val translations = adapter.getTranslations().toMutableList()
    val downloadedItemCount = currentSortedDownloads.size
    if (downloadedItemCount + 1 <= translations.size) {
      for (i in 0 until downloadedItemCount) {
        translations.removeAt(1)
      }
      translations.addAll(1, currentSortedDownloads)
      adapter.setTranslations(translations)
      adapter.notifyDataSetChanged()
    }
  }

  private fun onErrorDownloadTranslations() {
    translationSwipeRefresh.isRefreshing = false
    Snackbar
      .make(
        translationRecycler,
        R.string.error_getting_translation_list,
        Snackbar.LENGTH_SHORT
      )
      .show()
  }

  private fun onTranslationsUpdated(items: List<TranslationItem>) {
    translationSwipeRefresh.isRefreshing = false
    val itemsSparseArray = SparseIntArray(items.size)
    var i = 0
    val itemsSize = items.size
    while (i < itemsSize) {
      val (translation) = items[i]
      itemsSparseArray.put(translation.id, i)
      i++
    }
    allItems = items
    translationPositions = itemsSparseArray
    generateListItems()
  }

  private fun generateListItems() {
    val (downloaded, notDownloaded) = allItems.partition { it.exists() }

    // sort by display order
    val sortedDownloads = downloaded.sortedBy { it.displayOrder }

    val resultList = buildList {
      if (downloaded.isNotEmpty()) {
        add(TranslationHeader(getString(R.string.downloaded_translations)))
        addAll(sortedDownloads)
      }
      add(TranslationHeader(getString(R.string.available_translations)))
      addAll(notDownloaded)
    }

    val needsUpgrade = sortedDownloads.any { it.needsUpgrade() }
    if (!needsUpgrade) {
      quranSettings.setHaveUpdatedTranslations(false)
    }

    originalSortedDownloads = ArrayList(downloaded)
    currentSortedDownloads = ArrayList(downloaded)
    adapter.setTranslations(resultList)
    adapter.notifyDataSetChanged()
  }

  private fun downloadItem(translationRowData: TranslationRowData) {
    val selectedItem = translationRowData as TranslationItem
    if (selectedItem.exists() && !selectedItem.needsUpgrade()) {
      return
    }
    downloadingItem = selectedItem
    val (_, _, _, _, _, fileName, url) = selectedItem.translation
    clearDatabaseHandlerIfExists(fileName)
    if (downloadReceiver == null) {
      val downloadReceiver = DefaultDownloadReceiver(
        this,
        QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION
      )
      LocalBroadcastManager.getInstance(this).registerReceiver(
        downloadReceiver, IntentFilter(
          QuranDownloadNotifier.ProgressIntent.INTENT_NAME
        )
      )
      this.downloadReceiver = downloadReceiver
    }
    downloadReceiver!!.setListener(this)

    // actually start the download
    val destination = databaseDirectory
    Timber.d("downloading %s to %s", url, destination)
    if (selectedItem.exists()) {
      try {
        val f = File(destination, fileName)
        if (f.exists()) {
          val newPath = File(
            destination,
            fileName + UPGRADING_EXTENSION
          )
          if (newPath.exists()) {
            newPath.delete()
          }
          f.renameTo(newPath)
        }
      } catch (e: Exception) {
        Timber.d(e, "error backing database file up")
      }
    }

    // start the download
    val notificationTitle = selectedItem.name()
    val intent = getDownloadIntent(
      this, url,
      destination, notificationTitle, TRANSLATION_DOWNLOAD_KEY,
      QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION
    )
    var filename = selectedItem.translation.fileName
    if (url.endsWith("zip")) {
      filename += ".zip"
    }
    intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME, filename)
    startService(intent)
  }

  private fun removeItem(translationRowData: TranslationRowData) {
    val selectedItem = translationRowData as TranslationItem
    val msg = String.format(getString(R.string.remove_dlg_msg), selectedItem.name())
    val builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.remove_dlg_title)
      .setMessage(msg)
      .setPositiveButton(
        com.quran.mobile.common.ui.core.R.string.remove_button
      ) { _: DialogInterface?, _: Int ->
        if (removeTranslation(selectedItem.translation.fileName)) {
          val updatedItem = selectedItem.withTranslationRemoved()
          updateTranslationItem(updatedItem)

          // remove from active translations
          val settings = QuranSettings.getInstance(this)
          val activeTranslations = settings.activeTranslations
          activeTranslations.remove(selectedItem.translation.fileName)
          settings.activeTranslations = activeTranslations
          generateListItems()
        }
      }
      .setNegativeButton(
        com.quran.mobile.common.ui.core.R.string.cancel
      ) { dialog: DialogInterface, i: Int -> dialog.dismiss() }
    builder.show()
  }

  private fun sortedDownloadedItems(): List<TranslationItem> {
    return allItems.filter { it.exists() }.sortedBy { it.displayOrder }
  }

  private fun rankDownItem(targetRow: TranslationRowData) {
    val targetItem = targetRow as TranslationItem
    val targetTranslationId = targetItem.translation.id
    val targetIndex = currentSortedDownloads.indexOfFirst { it.translation.id == targetTranslationId }
    if (targetIndex >= 0) {
      val sortedDownloads = currentSortedDownloads.toMutableList()
      sortedDownloads.removeAt(targetIndex)
      val updatedItem = targetItem.withDisplayOrder(targetItem.displayOrder + 1)
      if (targetIndex + 1 < sortedDownloads.size) {
        sortedDownloads.add(targetIndex + 1, updatedItem)
      } else {
        sortedDownloads.add(updatedItem)
      }
      currentSortedDownloads = sortedDownloads
      updateDownloadedItems()
    }
  }

  private fun rankUpItem(targetRow: TranslationRowData) {
    val targetItem = targetRow as TranslationItem
    val targetTranslationId = targetItem.translation.id
    val targetIndex = currentSortedDownloads.indexOfFirst { it.translation.id == targetTranslationId }
    if (targetIndex >= 0) {
      val sortedDownloads = currentSortedDownloads.toMutableList()
      sortedDownloads.removeAt(targetIndex)
      val updatedItem = targetItem.withDisplayOrder(targetItem.displayOrder - 1)
      sortedDownloads.add(max(targetIndex - 1, 0), updatedItem)
      currentSortedDownloads = sortedDownloads
      updateDownloadedItems()
    }
  }

  private fun updateTranslationOrdersIfNecessary() {
    if (originalSortedDownloads != currentSortedDownloads) {
      val normalizedSortOrders: List<TranslationItem> =
        currentSortedDownloads.mapIndexed { index, item ->
          item.withDisplayOrder(index + 1)
        }

      originalSortedDownloads = normalizedSortOrders
      currentSortedDownloads = normalizedSortOrders
      scope.launch {
        presenter.updateItemOrdering(normalizedSortOrders)
      }
    }
  }

  private fun removeTranslation(fileName: String): Boolean {
    var path = quranFileUtils.getQuranDatabaseDirectory(this@TranslationManagerActivity)
    if (path != null) {
      path += File.separator + fileName
      val f = File(path)
      return f.delete()
    }
    return false
  }

  override fun startMenuAction(
    item: TranslationItem,
    downloadedItemActionListener: DownloadedItemActionListener?
  ) {
    this.downloadedItemActionListener = downloadedItemActionListener
    if (actionMode != null) {
      actionMode!!.finish()
      selectionListener.clearSelection()
    } else {
      selectionListener.handleSelection(item)
      actionMode = startSupportActionMode(ModeCallback())
    }
  }

  override fun finishMenuAction() {
    actionMode?.finish()
    selectionListener.clearSelection()
    downloadedItemActionListener = null
  }

  internal class TranslationSelectionListener(private val adapter: TranslationsAdapter) {
    fun handleSelection(item: TranslationItem?) {
      adapter.setSelectedItem(item)
    }

    fun clearSelection() {
      adapter.setSelectedItem(null)
    }
  }

  private inner class ModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      val inflater = menuInflater
      inflater.inflate(R.menu.downloaded_translation_menu, menu)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      val itemId = item.itemId
      when (itemId) {
          R.id.dtm_delete -> {
            downloadedItemActionListener?.handleDeleteItemAction()
            endAction()
          }
          R.id.dtm_move_up -> {
            downloadedItemActionListener?.handleRankUpItemAction()
          }
          R.id.dtm_move_down -> {
            downloadedItemActionListener?.handleRankDownItemAction()
          }
      }
      return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      if (mode === actionMode) {
        selectionListener.clearSelection()
        actionMode = null
        updateTranslationOrdersIfNecessary()
      }
    }

    private fun endAction() {
      if (actionMode != null) {
        selectionListener.clearSelection()
        actionMode!!.finish()
      }
    }
  }

  companion object {
    const val TRANSLATION_DOWNLOAD_KEY = "TRANSLATION_DOWNLOAD_KEY"
    private const val UPGRADING_EXTENSION = ".old"
  }
}
