package com.quran.labs.androidquran.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

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
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import timber.log.Timber;

public class TranslationManagerActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener {

  public static final String TRANSLATION_DOWNLOAD_KEY = "TRANSLATION_DOWNLOAD_KEY";
  private static final String UPGRADING_EXTENSION = ".old";

  private List<TranslationRowData> mItems;
  private List<TranslationItem> mAllItems;
  private SparseIntArray mTranslationPositions;

  private ListView mListView;
  private TextView mMessageArea;
  private TranslationsAdapter mAdapter;
  private TranslationItem mDownloadingItem;
  private String mDatabaseDirectory;
  private QuranSettings mQuranSettings;

  @Inject TranslationManagerPresenter mPresenter;
  private DefaultDownloadReceiver mDownloadReceiver = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

    ((QuranApplication) getApplication()).getApplicationComponent().inject(this);

    setContentView(R.layout.translation_manager);
    mListView = (ListView) findViewById(R.id.translation_list);
    mAdapter = new TranslationsAdapter(this, null);
    mListView.setAdapter(mAdapter);
    mMessageArea = (TextView) findViewById(R.id.message_area);
    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView,
          View view, int pos, long id) {
        downloadItem(pos);
      }
    });

    mDatabaseDirectory = QuranFileUtils.getQuranDatabaseDirectory(this);

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.prefs_translations);
    }

    mQuranSettings = QuranSettings.getInstance(this);
    mPresenter.bind(this);
    mPresenter.getTranslationsList(false);
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
    mPresenter.unbind(this);
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
    if (mDownloadingItem != null) {
      if (mDownloadingItem.exists()) {
        try {
          File f = new File(mDatabaseDirectory,
              mDownloadingItem.translation.filename + UPGRADING_EXTENSION);
          if (f.exists()) {
            f.delete();
          }
        } catch (Exception e) {
          Timber.d(e, "error removing old database file");
        }
      }
      TranslationItem updated = mDownloadingItem.withTranslationVersion(
          mDownloadingItem.translation.currentVersion);
      updateTranslationItem(updated);
    }
    mDownloadingItem = null;
    generateListItems();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    if (mDownloadingItem != null && mDownloadingItem.exists()) {
      try {
        File f = new File(mDatabaseDirectory,
            mDownloadingItem.translation.filename + UPGRADING_EXTENSION);
        File destFile = new File(mDatabaseDirectory, mDownloadingItem.translation.filename);
        if (f.exists() && !destFile.exists()) {
          f.renameTo(destFile);
        } else {
          f.delete();
        }
      } catch (Exception e) {
        Timber.d(e, "error restoring translation after failed download");
      }
    }
    mDownloadingItem = null;
  }

  private void updateTranslationItem(TranslationItem updated) {
    int id = updated.translation.id;
    int allItemsIndex = mTranslationPositions.get(id);
    if (mAllItems != null && mAllItems.size() > allItemsIndex) {
      mAllItems.remove(allItemsIndex);
      mAllItems.add(allItemsIndex, updated);
    }
    mPresenter.updateItem(updated);
  }

  public void onErrorDownloadTranslations() {
    mMessageArea.setText(R.string.error_getting_translation_list);
  }

  public void onTranslationsUpdated(List<TranslationItem> items) {
    SparseIntArray itemsSparseArray = new SparseIntArray(items.size());
    for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
      TranslationItem item = items.get(i);
      itemsSparseArray.put(item.translation.id, i);
    }
    mAllItems = items;
    mTranslationPositions = itemsSparseArray;

    mMessageArea.setVisibility(View.GONE);
    mListView.setVisibility(View.VISIBLE);
    generateListItems();
  }

  private void generateListItems() {
    if (mAllItems == null) {
      return;
    }

    List<TranslationItem> downloaded = new ArrayList<>();
    List<TranslationItem> notDownloaded = new ArrayList<>();
    for (int i = 0, mAllItemsSize = mAllItems.size(); i < mAllItemsSize; i++) {
      TranslationItem item = mAllItems.get(i);
      if (item.exists()) {
        downloaded.add(item);
      } else {
        notDownloaded.add(item);
      }
    }

    List<TranslationRowData> res = new ArrayList<>();
    if (downloaded.size() > 0) {
      TranslationHeader hdr = new TranslationHeader(getString(R.string.downloaded_translations));
      res.add(hdr);

      boolean needsUpgrade = false;
      for (TranslationItem item : downloaded) {
        res.add(item);
        needsUpgrade = needsUpgrade || item.needsUpgrade();
      }

      if (!needsUpgrade) {
        mQuranSettings.setHaveUpdatedTranslations(false);
      }
    }

    res.add(new TranslationHeader(getString(R.string.available_translations)));

    for (TranslationItem item : notDownloaded) {
      res.add(item);
    }

    mItems = res;
    mAdapter.setData(mItems);
    mAdapter.notifyDataSetChanged();
  }

  private void downloadItem(int pos) {
    if (mItems == null || !(mAdapter.getItem(pos) instanceof TranslationItem)) {
      return;
    }

    TranslationItem selectedItem = (TranslationItem) mAdapter.getItem(pos);
    if (selectedItem.exists() && !selectedItem.needsUpgrade()) {
      return;
    }

    mDownloadingItem = selectedItem;
    if (mDownloadReceiver == null) {
      mDownloadReceiver = new DefaultDownloadReceiver(this,
          QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
      LocalBroadcastManager.getInstance(this).registerReceiver(
          mDownloadReceiver, new IntentFilter(
              QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    }
    mDownloadReceiver.setListener(this);

    // actually start the download
    String url = selectedItem.translation.fileUrl;
    if (selectedItem.translation.fileUrl == null) {
      return;
    }
    String destination = mDatabaseDirectory;
    Timber.d("downloading %s to %s", url, destination);

    if (selectedItem.exists()) {
      try {
        File f = new File(destination, selectedItem.translation.filename);
        if (f.exists()) {
          File newPath = new File(destination,
              selectedItem.translation.filename + UPGRADING_EXTENSION);
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
    String filename = selectedItem.translation.filename;
    if (url.endsWith("zip")) {
      filename += ".zip";
    }
    intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME, filename);
    startService(intent);
  }

  private void removeItem(final int pos) {
    if (mItems == null || mAdapter == null) {
      return;
    }

    final TranslationItem selectedItem =
        (TranslationItem) mAdapter.getItem(pos);
    String msg = String.format(getString(R.string.remove_dlg_msg), selectedItem.name());
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.remove_dlg_title)
        .setMessage(msg)
        .setPositiveButton(R.string.remove_button,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                QuranFileUtils.removeTranslation(TranslationManagerActivity.this,
                    selectedItem.translation.filename);
                TranslationItem updatedItem = selectedItem.withTranslationRemoved();
                updateTranslationItem(updatedItem);
                String current = mQuranSettings.getActiveTranslation();
                if (current.equals(selectedItem.translation.filename)) {
                  mQuranSettings.removeActiveTranslation();
                }
                generateListItems();
              }
            })
        .setNegativeButton(R.string.cancel,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
              }
            });
    builder.show();
  }

  private class TranslationsAdapter extends BaseAdapter {

    private LayoutInflater mInflater;
    private List<TranslationRowData> mElements;
    private int TYPE_ITEM = 0;
    private int TYPE_SEPARATOR = 1;

    public TranslationsAdapter(Context context,
        List<TranslationRowData> elements) {
      mInflater = LayoutInflater.from(context);
      mElements = elements;
    }

    public void setData(List<TranslationRowData> items) {
      mElements = items;
    }

    @Override
    public int getCount() {
      return mElements == null ? 0 : mElements.size();
    }

    @Override
    public int getItemViewType(int position) {
      return (mElements.get(position).isSeparator()) ?
          TYPE_SEPARATOR : TYPE_ITEM;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }


    @Override
    public TranslationRowData getItem(int position) {
      return mElements.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder;

      if (convertView == null) {
        holder = new ViewHolder();
        if (getItemViewType(position) == TYPE_ITEM) {
          convertView = mInflater.inflate(
              R.layout.translation_row, parent, false);
          holder.translationTitle = (TextView) convertView
              .findViewById(R.id.translation_title);
          holder.translationInfo = (TextView) convertView
              .findViewById(R.id.translation_info);
          holder.leftImage = (ImageView) convertView
              .findViewById(R.id.left_image);
          holder.rightImage = (ImageView) convertView
              .findViewById(R.id.right_image);
        } else {
          convertView = mInflater.inflate(R.layout.translation_sep, parent, false);
          holder.separatorText = (TextView) convertView
              .findViewById(R.id.separator_txt);
        }
        convertView.setTag(holder);
      } else {
        holder = (ViewHolder) convertView.getTag();
      }

      TranslationRowData rowItem = mElements.get(position);
      if (getItemViewType(position) == TYPE_SEPARATOR) {
        holder.separatorText.setText(rowItem.name());
      } else {
        TranslationItem item = (TranslationItem) rowItem;
        holder.translationTitle.setText(item.name());
        if (TextUtils.isEmpty(item.translation.translatorNameLocalized)) {
          holder.translationInfo.setVisibility(View.GONE);
        } else {
          holder.translationInfo.setText(item.translation.translatorNameLocalized);
          holder.translationInfo.setVisibility(View.VISIBLE);
        }

        if (item.exists()) {
          if (item.needsUpgrade()) {
            holder.leftImage.setImageResource(R.drawable.ic_download);
            holder.leftImage.setVisibility(View.VISIBLE);

            holder.translationInfo.setText(R.string.update_available);
            holder.translationInfo.setVisibility(View.VISIBLE);
          } else {
            holder.leftImage.setVisibility(View.GONE);
          }
          holder.rightImage.setImageResource(R.drawable.ic_cancel);
          holder.rightImage.setVisibility(View.VISIBLE);
          holder.rightImage.setContentDescription(getString(R.string.remove_button));

          final int pos = position;
          holder.rightImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              removeItem(pos);
            }
          });
        } else {
          holder.leftImage.setVisibility(View.GONE);
          holder.rightImage.setImageResource(R.drawable.ic_download);
          holder.rightImage.setVisibility(View.VISIBLE);
          holder.rightImage.setOnClickListener(null);
          holder.rightImage.setClickable(false);
          holder.rightImage.setContentDescription(null);
        }
      }

      return convertView;
    }

    @Override
    public boolean isEnabled(int position) {
      return getItemViewType(position) != TYPE_SEPARATOR;
    }

    class ViewHolder {

      TextView translationTitle;
      TextView translationInfo;
      ImageView leftImage;
      ImageView rightImage;

      TextView separatorText;
    }
  }
}
