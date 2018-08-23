package com.quran.labs.androidquran.ui;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.SparseIntArray;
import android.view.MenuItem;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.translation.TranslationHeader;
import com.quran.labs.androidquran.dao.translation.TranslationItem;
import com.quran.labs.androidquran.dao.translation.TranslationRowData;
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.adapter.TranslationsAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;
import timber.log.Timber;

public class TranslationManagerActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener {

  public static final String TRANSLATION_DOWNLOAD_KEY = "TRANSLATION_DOWNLOAD_KEY";
  private static final String UPGRADING_EXTENSION = ".old";

  private List<TranslationItem> allItems;
  private SparseIntArray translationPositions;

  private TranslationsAdapter adapter;
  private TranslationItem downloadingItem;
  private String databaseDirectory;
  private QuranSettings quranSettings;
  private DefaultDownloadReceiver mDownloadReceiver = null;

  private Disposable onClickDownloadDisposable;
  private Disposable onClickRemoveDisposable;

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

    RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
    translationRecycler.setLayoutManager(mLayoutManager);

    adapter = new TranslationsAdapter(this);
    translationRecycler.setAdapter(adapter);

    databaseDirectory = quranFileUtils.getQuranDatabaseDirectory(this);

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.prefs_translations);
    }

    quranSettings = QuranSettings.getInstance(this);
    presenter.bind(this);
    presenter.getTranslationsList(false);
    onClickDownloadDisposable = adapter.getOnClickDownloadSubject().subscribe(this::downloadItem);
    onClickRemoveDisposable = adapter.getOnClickRemoveSubject().subscribe(this::removeItem);

    translationSwipeRefresh.setOnRefreshListener(this::onRefresh);
  }

  @Override
  public void onStop() {
    if (mDownloadReceiver != null) {
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    presenter.unbind(this);
    onClickDownloadDisposable.dispose();
    onClickRemoveDisposable.dispose();
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
      TranslationItem updated = downloadingItem.withTranslationVersion(
          downloadingItem.getTranslation().getCurrentVersion());
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
    for (int i = 0, mAllItemsSize = allItems.size(); i < mAllItemsSize; i++) {
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

      boolean needsUpgrade = false;
      for (TranslationItem item : downloaded) {
        result.add(item);
        needsUpgrade = needsUpgrade || item.needsUpgrade();
      }

      if (!needsUpgrade) {
        quranSettings.setHaveUpdatedTranslations(false);
      }
    }

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
    if (mDownloadReceiver == null) {
      mDownloadReceiver = new DefaultDownloadReceiver(this,
          QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
      LocalBroadcastManager.getInstance(this).registerReceiver(
          mDownloadReceiver, new IntentFilter(
              QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    }
    mDownloadReceiver.setListener(this);

    // actually start the download
    String url = selectedItem.getTranslation().getFileUrl();
    if (selectedItem.getTranslation().getFileUrl() == null) {
      return;
    }
    String destination = databaseDirectory;
    Timber.d("downloading %s to %s", url, destination);

    if (selectedItem.exists()) {
      try {
        File f = new File(destination, selectedItem.getTranslation().getFileName());
        if (f.exists()) {
          File newPath = new File(destination,
              selectedItem.getTranslation().getFileName() + UPGRADING_EXTENSION);
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
              quranFileUtils.removeTranslation(TranslationManagerActivity.this,
                  selectedItem.getTranslation().getFileName());
              TranslationItem updatedItem = selectedItem.withTranslationRemoved();
              updateTranslationItem(updatedItem);

              // remove from active translations
              QuranSettings settings = QuranSettings.getInstance(this);
              Set<String> activeTranslations = settings.getActiveTranslations();
              activeTranslations.remove(selectedItem.getTranslation().getFileName());
              settings.setActiveTranslations(activeTranslations);
              generateListItems();
            })
        .setNegativeButton(R.string.cancel,
            (dialog, i) -> dialog.dismiss());
    builder.show();
  }

}
