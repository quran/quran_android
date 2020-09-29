package com.quran.labs.androidquran.ui;

import com.google.android.material.snackbar.Snackbar;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.translation.Translation;
import com.quran.labs.androidquran.dao.translation.TranslationHeader;
import com.quran.labs.androidquran.dao.translation.TranslationItem;
import com.quran.labs.androidquran.dao.translation.TranslationItemDisplaySort;
import com.quran.labs.androidquran.dao.translation.TranslationRowData;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.adapter.DownloadedItemActionListener;
import com.quran.labs.androidquran.ui.adapter.DownloadedMenuActionListener;
import com.quran.labs.androidquran.ui.adapter.TranslationsAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

public class TranslationManagerActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener, DownloadedMenuActionListener {

  public static final String TRANSLATION_DOWNLOAD_KEY = "TRANSLATION_DOWNLOAD_KEY";
  private static final String UPGRADING_EXTENSION = ".old";

  private List<TranslationItem> allItems;
  private List<TranslationItem> currentSortedDownloads;
  private List<TranslationItem> originalSortedDownloads;

  private SparseIntArray translationPositions;

  private TranslationsAdapter adapter;
  private TranslationItem downloadingItem;
  private String databaseDirectory;
  private QuranSettings quranSettings;
  private DefaultDownloadReceiver downloadReceiver = null;

  private Disposable onClickDownloadDisposable;
  private Disposable onClickRemoveDisposable;
  private Disposable onClickRankUpDisposable;
  private Disposable onClickRankDownDisposable;

  private ActionMode actionMode;
  private TranslationSelectionListener selectionListener;
  private DownloadedItemActionListener downloadedItemActionListener;

  @Inject TranslationManagerPresenter presenter;
  @Inject QuranFileUtils quranFileUtils;

  SwipeRefreshLayout translationSwipeRefresh;
  RecyclerView translationRecycler;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((QuranApplication) getApplication()).getApplicationComponent().inject(this);
    setContentView(R.layout.translation_manager);
    translationSwipeRefresh = findViewById(R.id.translation_swipe_refresh);
    translationRecycler = findViewById(R.id.translation_recycler);

    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
    translationRecycler.setLayoutManager(layoutManager);

    adapter = new TranslationsAdapter(this);
    translationRecycler.setAdapter(adapter);
    selectionListener = new TranslationSelectionListener(adapter);

    databaseDirectory = quranFileUtils.getQuranDatabaseDirectory(this);

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.prefs_translations);
    }

    quranSettings = QuranSettings.getInstance(this);
    onClickDownloadDisposable = adapter.getOnClickDownloadSubject().subscribe(this::downloadItem);
    onClickRemoveDisposable = adapter.getOnClickRemoveSubject().subscribe(this::removeItem);
    onClickRankUpDisposable = adapter.getOnClickRankUpSubject().subscribe(this::rankUpItem);
    onClickRankDownDisposable = adapter.getOnClickRankDownSubject().subscribe(this::rankDownItem);

    translationSwipeRefresh.setOnRefreshListener(this::onRefresh);
    presenter.bind(this);
    translationSwipeRefresh.setRefreshing(true);
    presenter.getTranslationsList(false);
  }

  @Override
  public void onStop() {
    if (downloadReceiver != null) {
      downloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(downloadReceiver);
      downloadReceiver = null;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    presenter.unbind(this);
    onClickDownloadDisposable.dispose();
    onClickRemoveDisposable.dispose();
    onClickRankUpDisposable.dispose();
    onClickRankDownDisposable.dispose();
    super.onDestroy();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void handleDownloadSuccess() {
    if (downloadingItem != null) {
      if (downloadingItem.exists()) {
        try {
          File f = new File(databaseDirectory,
              downloadingItem.getTranslation().getFileName() + UPGRADING_EXTENSION);
          if (f.exists()) {
            f.delete();
          }
        } catch (Exception e) {
          Timber.d(e, "error removing old database file");
        }
      }

      List<TranslationItem> sortedItems = sortedDownloadedItems();
      int lastDisplayOrder = sortedItems.isEmpty() ? 1 :
              sortedItems.get(sortedItems.size() - 1).getDisplayOrder();

      final Translation translation = downloadingItem.getTranslation();
      TranslationItem updated = new TranslationItem(translation,
              translation.getCurrentVersion(), lastDisplayOrder + 1);
      updateTranslationItem(updated);

      // update active translations and add this item to it
      QuranSettings settings = QuranSettings.getInstance(this);
      Set<String> activeTranslations = settings.getActiveTranslations();
      activeTranslations.add(downloadingItem.getTranslation().getFileName());
      settings.setActiveTranslations(activeTranslations);
    }
    downloadingItem = null;
    generateListItems();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    if (downloadingItem != null && downloadingItem.exists()) {
      try {
        File f = new File(databaseDirectory,
            downloadingItem.getTranslation().getFileName() + UPGRADING_EXTENSION);
        File destFile = new File(databaseDirectory, downloadingItem.getTranslation().getFileName());
        if (f.exists() && !destFile.exists()) {
          f.renameTo(destFile);
        } else {
          f.delete();
        }
      } catch (Exception e) {
        Timber.d(e, "error restoring translation after failed download");
      }
    }
    downloadingItem = null;
  }

  private void onRefresh() {
    presenter.getTranslationsList(true);
  }

  private void updateTranslationItem(TranslationItem updated) {
    int id = updated.getTranslation().getId();
    int allItemsIndex = translationPositions.get(id);
    if (allItems != null && allItems.size() > allItemsIndex) {
      allItems.remove(allItemsIndex);
      allItems.add(allItemsIndex, updated);
    }
    presenter.updateItem(updated);
  }

  private void updateDownloadedItems() {
    final List<TranslationRowData> translations = adapter.getTranslations();
    final int downloadedItemCount = currentSortedDownloads.size();
    if (downloadedItemCount + 1 <= translations.size()) {
      for (int i = 0; i < downloadedItemCount; i++) {
        translations.remove(1);
      }

      translations.addAll(1, currentSortedDownloads);
      adapter.setTranslations(translations);
      adapter.notifyDataSetChanged();
    }
  }

  public void onErrorDownloadTranslations() {
    translationSwipeRefresh.setRefreshing(false);
    Snackbar
        .make(translationRecycler, R.string.error_getting_translation_list, Snackbar.LENGTH_SHORT)
        .show();
  }

  public void onTranslationsUpdated(List<TranslationItem> items) {
    translationSwipeRefresh.setRefreshing(false);
    SparseIntArray itemsSparseArray = new SparseIntArray(items.size());
    for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
      TranslationItem item = items.get(i);
      itemsSparseArray.put(item.getTranslation().getId(), i);
    }
    allItems = items;
    translationPositions = itemsSparseArray;

    generateListItems();
  }

  private void generateListItems() {
    if (allItems == null) {
      return;
    }

    List<TranslationItem> downloaded = new ArrayList<>();
    List<TranslationItem> notDownloaded = new ArrayList<>();
    for (int i = 0, allItemsSize = allItems.size(); i < allItemsSize; i++) {
      TranslationItem item = allItems.get(i);
      if (item.exists()) {
        downloaded.add(item);
      } else {
        notDownloaded.add(item);
      }
    }

    List<TranslationRowData> result = new ArrayList<>();
    if (downloaded.size() > 0) {
      TranslationHeader hdr = new TranslationHeader(getString(R.string.downloaded_translations));
      result.add(hdr);

      // sort by display order
      Collections.sort(downloaded, new TranslationItemDisplaySort());

      boolean needsUpgrade = false;
      for (TranslationItem item : downloaded) {
        result.add(item);
        needsUpgrade = needsUpgrade || item.needsUpgrade();
      }

      if (!needsUpgrade) {
        quranSettings.setHaveUpdatedTranslations(false);
      }
    }
    originalSortedDownloads = new ArrayList<>(downloaded);
    currentSortedDownloads = new ArrayList<>(downloaded);

    result.add(new TranslationHeader(getString(R.string.available_translations)));

    result.addAll(notDownloaded);

    adapter.setTranslations(result);
    adapter.notifyDataSetChanged();
  }

  private void downloadItem(TranslationRowData translationRowData) {
    TranslationItem selectedItem = (TranslationItem) translationRowData;
    if (selectedItem.exists() && !selectedItem.needsUpgrade()) {
      return;
    }

    downloadingItem = selectedItem;

    final Translation translation = selectedItem.getTranslation();
    DatabaseHandler.clearDatabaseHandlerIfExists(translation.getFileName());
    if (downloadReceiver == null) {
      downloadReceiver = new DefaultDownloadReceiver(this,
          QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
      LocalBroadcastManager.getInstance(this).registerReceiver(
          downloadReceiver, new IntentFilter(
              QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    }
    downloadReceiver.setListener(this);

    // actually start the download
    String url = translation.getFileUrl();
    String destination = databaseDirectory;
    Timber.d("downloading %s to %s", url, destination);

    if (selectedItem.exists()) {
      try {
        File f = new File(destination, translation.getFileName());
        if (f.exists()) {
          File newPath = new File(destination,
              translation.getFileName() + UPGRADING_EXTENSION);
          if (newPath.exists()) {
            newPath.delete();
          }
          f.renameTo(newPath);
        }
      } catch (Exception e) {
        Timber.d(e, "error backing database file up");
      }
    }

    // start the download
    String notificationTitle = selectedItem.name();
    Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
        destination, notificationTitle, TRANSLATION_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
    String filename = selectedItem.getTranslation().getFileName();
    if (url.endsWith("zip")) {
      filename += ".zip";
    }
    intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME, filename);
    startService(intent);
  }

  private void removeItem(final TranslationRowData translationRowData) {
    if (adapter == null) {
      return;
    }

    final TranslationItem selectedItem =
        (TranslationItem) translationRowData;
    String msg = String.format(getString(R.string.remove_dlg_msg), selectedItem.name());
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.remove_dlg_title)
        .setMessage(msg)
        .setPositiveButton(R.string.remove_button,
            (dialog, id) -> {
              if (removeTranslation(selectedItem.getTranslation().getFileName())) {
                TranslationItem updatedItem = selectedItem.withTranslationRemoved();
                updateTranslationItem(updatedItem);

                // remove from active translations
                QuranSettings settings = QuranSettings.getInstance(this);
                Set<String> activeTranslations = settings.getActiveTranslations();
                activeTranslations.remove(selectedItem.getTranslation().getFileName());
                settings.setActiveTranslations(activeTranslations);
                generateListItems();
              }
            })
        .setNegativeButton(R.string.cancel,
            (dialog, i) -> dialog.dismiss());
    builder.show();
  }

  private List<TranslationItem> sortedDownloadedItems() {
    final ArrayList<TranslationItem> result = new ArrayList<>();
    for (TranslationItem item : allItems) {
      if (item.exists()) result.add(item);
    }
    Collections.sort(result, new TranslationItemDisplaySort());
    return result;
  }

  private void rankDownItem(TranslationRowData targetRow) {
    final TranslationItem targetItem = (TranslationItem) targetRow;
    final int targetTranslationId = targetItem.getTranslation().getId();

    int targetIndex = -1;
    for (int i = 0; i < currentSortedDownloads.size(); i++) {
      if (currentSortedDownloads.get(i).getTranslation().getId() == targetTranslationId) {
        targetIndex = i;
        break;
      }
    }

    if (targetIndex >= 0) {
      currentSortedDownloads.remove(targetIndex);
      final TranslationItem updatedItem =
              targetItem.withDisplayOrder(targetItem.getDisplayOrder() + 1);
      if (targetIndex + 1 < currentSortedDownloads.size()) {
        currentSortedDownloads.add(targetIndex + 1, updatedItem);
      } else {
        currentSortedDownloads.add(updatedItem);
      }
      updateDownloadedItems();
    }
  }

  private void rankUpItem(TranslationRowData targetRow) {
    final TranslationItem targetItem = (TranslationItem) targetRow;
    final int targetTranslationId = targetItem.getTranslation().getId();

    int targetIndex = -1;
    for (int i = 0; i < currentSortedDownloads.size(); i++) {
      if (currentSortedDownloads.get(i).getTranslation().getId() == targetTranslationId) {
        targetIndex = i;
        break;
      }
    }

    if (targetIndex >= 0) {
      currentSortedDownloads.remove(targetIndex);
      final TranslationItem updatedItem =
              targetItem.withDisplayOrder(targetItem.getDisplayOrder() - 1);
      currentSortedDownloads.add(Math.max(targetIndex - 1, 0), updatedItem);
      updateDownloadedItems();
    }
  }

  private void updateTranslationOrdersIfNecessary() {
    if (!originalSortedDownloads.equals(currentSortedDownloads)) {
      final List<TranslationItem> normalizedSortOrders = new ArrayList<>();
      for (int i = 0; i < currentSortedDownloads.size(); i++) {
        normalizedSortOrders.add(currentSortedDownloads.get(i).withDisplayOrder(i + 1));
      }
      originalSortedDownloads.clear();
      originalSortedDownloads.addAll(normalizedSortOrders);
      currentSortedDownloads.clear();
      currentSortedDownloads.addAll(normalizedSortOrders);
      presenter.updateItemOrdering(normalizedSortOrders);
    }
  }

  private boolean removeTranslation(String fileName) {
    String path = quranFileUtils.getQuranDatabaseDirectory(TranslationManagerActivity.this);
    if (path != null) {
      path += File.separator + fileName;
      File f = new File(path);
      return f.delete();
    }
    return false;
  }

  @Override
  public void startMenuAction(TranslationItem item, DownloadedItemActionListener aDownloadedItemActionListener) {
    downloadedItemActionListener = aDownloadedItemActionListener;
    if (actionMode != null) {
      actionMode.finish();
      selectionListener.clearSelection();
    } else {
      selectionListener.handleSelection(item);
      actionMode = startSupportActionMode(new ModeCallback());
    }
  }

  @Override
  public void finishMenuAction() {
    if (actionMode != null) {
      actionMode.finish();
    }
    selectionListener.clearSelection();
    downloadedItemActionListener = null;
  }

  class TranslationSelectionListener {
    private final TranslationsAdapter adapter;

    TranslationSelectionListener(TranslationsAdapter anAdapter) {
      adapter = anAdapter;
    }

    void handleSelection(TranslationItem item) {
      adapter.setSelectedItem(item);
    }

    void clearSelection() {
      adapter.setSelectedItem(null);
    }
  }

  private class ModeCallback implements ActionMode.Callback  {
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.downloaded_translation_menu, menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.dtm_delete:
          if (downloadedItemActionListener != null) downloadedItemActionListener.handleDeleteItemAction();
          endAction();
          break;
        case R.id.dtm_move_up:
          if (downloadedItemActionListener != null) downloadedItemActionListener.handleRankUpItemAction();
          break;
        case R.id.dtm_move_down:
          if (downloadedItemActionListener != null) downloadedItemActionListener.handleRankDownItemAction();
          break;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      if (mode == actionMode) {
        selectionListener.clearSelection();
        actionMode = null;
        updateTranslationOrdersIfNecessary();
      }
    }

    private void endAction() {
      if (actionMode != null) {
        selectionListener.clearSelection();
        actionMode.finish();
      }
    }
  }
}
