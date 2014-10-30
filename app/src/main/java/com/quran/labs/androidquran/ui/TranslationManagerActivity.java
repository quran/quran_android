package com.quran.labs.androidquran.ui;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.task.TranslationListTask;
import com.quran.labs.androidquran.util.QuranFileUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TranslationManagerActivity extends ActionBarActivity
        implements DefaultDownloadReceiver.SimpleDownloadListener,
                   TranslationListTask.TranslationsUpdatedListener {
   public static final String TAG = "TranslationManager";
   public static final String TRANSLATION_DOWNLOAD_KEY =
           "TRANSLATION_DOWNLOAD_KEY";
   private static final String UPGRADING_EXTENSION = ".old";

   private List<TranslationItem> mItems;
   private List<TranslationItem> mAllItems;

   private ListView mListView;
   private TextView mMessageArea;
   private TranslationsAdapter mAdapter;
   private TranslationItem mDownloadingItem;
   private String mDatabaseDirectory;
   private SharedPreferences mSharedPreferences = null;

   private DefaultDownloadReceiver mDownloadReceiver = null;
   private TranslationListTask mTask = null;

   @Override
   public void onCreate(Bundle savedInstanceState){
      setTheme(R.style.QuranAndroid);
      super.onCreate(savedInstanceState);
      //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

      setContentView(R.layout.translation_manager);
      mListView = (ListView)findViewById(R.id.translation_list);
      mAdapter = new TranslationsAdapter(this, null);
      mListView.setAdapter(mAdapter);
      mMessageArea = (TextView)findViewById(R.id.message_area);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         @Override
         public void onItemClick(AdapterView<?> adapterView,
                                 View view, int pos, long id) {
            downloadItem(pos);
         }
      });

      setSupportProgressBarIndeterminateVisibility(true);
      mDatabaseDirectory = QuranFileUtils.getQuranDatabaseDirectory(this);

      final ActionBar actionBar = getSupportActionBar();
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.prefs_translations);

      mSharedPreferences = PreferenceManager
              .getDefaultSharedPreferences(getApplicationContext());
      mTask = new TranslationListTask(this, this);
      mTask.execute();
   }

   @Override
   public void onStop(){
      if (mDownloadReceiver != null){
         mDownloadReceiver.setListener(null);
         LocalBroadcastManager.getInstance(this)
              .unregisterReceiver(mDownloadReceiver);
         mDownloadReceiver = null;
      }
      super.onStop();
   }

   @Override
   protected void onDestroy() {
      if (mTask != null){
         mTask.cancel(true);
      }
      mTask = null;
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
   public void handleDownloadSuccess(){
      if (mDownloadingItem != null){
         if (mDownloadingItem.exists){
            try {
               File f = new File(mDatabaseDirectory,
                    mDownloadingItem.filename + UPGRADING_EXTENSION);
               if (f.exists()){ f.delete(); }
            }
            catch (Exception e){
               Log.d(TAG, "error removing old database file", e);
            }
         }
         mDownloadingItem.exists = true;
         mDownloadingItem.localVersion = mDownloadingItem.latestVersion;

         writeDatabaseUpdate(mDownloadingItem);
      }
      mDownloadingItem = null;
      generateListItems();
   }

   @Override
   public void handleDownloadFailure(int errId){
      if (mDownloadingItem != null && mDownloadingItem.exists){
         try {
            File f = new File(mDatabaseDirectory,
                    mDownloadingItem.filename + UPGRADING_EXTENSION);
            File destFile = new File(mDatabaseDirectory,
                    mDownloadingItem.filename);
            if (f.exists() && !destFile.exists()){
               f.renameTo(destFile);
            }
            else { f.delete(); }
         }
         catch (Exception e){
            Log.d(TAG, "error restoring translation after failed download", e);
         }
      }
      mDownloadingItem = null;
   }

   private void writeDatabaseUpdate(TranslationItem item){
      final List<TranslationItem> updates =
              new ArrayList<TranslationItem>();
      updates.add(item);

      final Activity activity = this;
      new Thread(new Runnable() {
         @Override
         public void run() {
            TranslationsDBAdapter adapter =
                    new TranslationsDBAdapter(activity);
            adapter.writeTranslationUpdates(updates);
            adapter.close();
         }
      }).start();
   }

   @Override
   public void translationsUpdated(List<TranslationItem> items){
      mAllItems = items;
      setSupportProgressBarIndeterminateVisibility(false);

      if (mAllItems == null){
         mMessageArea.setText(R.string.error_getting_translation_list);
      }
      else {
         mMessageArea.setVisibility(View.GONE);
         mListView.setVisibility(View.VISIBLE);
         generateListItems();
      }
      mTask = null;
   }

   private void generateListItems(){
      if (mAllItems == null){ return; }

      List<TranslationItem> downloaded = new ArrayList<TranslationItem>();
      List<TranslationItem> notDownloaded =
              new ArrayList<TranslationItem>();

      for (TranslationItem item : mAllItems){
         if (item.exists){ downloaded.add(item); }
         else { notDownloaded.add(item); }
      }

      List<TranslationItem> res = new ArrayList<TranslationItem>();

      if (downloaded.size() > 0) {
        TranslationItem hdr = new TranslationItem(
            getString(R.string.downloaded_translations));
        hdr.isSeparator = true;
        res.add(hdr);

        boolean needsUpgrade = false;
        for (TranslationItem item : downloaded) {
          res.add(item);
          if (hasUpgrade(item)) {
            needsUpgrade = true;
          }
        }

        if (!needsUpgrade) {
          mSharedPreferences.edit()
              .putBoolean(Constants.PREF_HAVE_UPDATED_TRANSLATIONS,
                  needsUpgrade).commit();
        }
      }

     TranslationItem hdr = new TranslationItem(
         getString(R.string.available_translations));
      hdr.isSeparator = true;
      res.add(hdr);

      for (TranslationItem item : notDownloaded){
         res.add(item);
      }

      mItems = res;
      mAdapter.setData(mItems);
      mAdapter.notifyDataSetChanged();
   }

   private void downloadItem(int pos){
      if (mItems == null || mAdapter == null){ return; }

      TranslationItem selectedItem =
              (TranslationItem)mAdapter.getItem(pos);
      if (selectedItem == null){ return; }
      if (selectedItem.exists &&
              (selectedItem.latestVersion <= 0 ||
               selectedItem.localVersion == null ||
               selectedItem.latestVersion <= selectedItem.localVersion)){
         return;
      }

      mDownloadingItem = selectedItem;

      if (mDownloadReceiver == null){
         mDownloadReceiver = new DefaultDownloadReceiver(this,
                 QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
         LocalBroadcastManager.getInstance(this).registerReceiver(
                 mDownloadReceiver, new IntentFilter(
                 QuranDownloadService.ProgressIntent.INTENT_NAME));
      }
      mDownloadReceiver.setListener(this);

      // actually start the download
      String url = selectedItem.url;
      if (selectedItem.url == null){ return; }
      String destination = mDatabaseDirectory;
      Log.d(TAG, "downloading " + url + " to " + destination);

      if (selectedItem.exists){
         try {
            File f = new File(destination, selectedItem.filename);
            if (f.exists()){
               File newPath = new File(destination,
                       selectedItem.filename + UPGRADING_EXTENSION);
               if (newPath.exists()){ newPath.delete(); }
               f.renameTo(newPath);
            }
         }
         catch (Exception e){
            Log.d(TAG, "error backing database file up", e);
         }
      }

      // start the download
      String notificationTitle = selectedItem.name;
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
              destination, notificationTitle, TRANSLATION_DOWNLOAD_KEY,
              QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
      String filename = selectedItem.filename;
      if (url.endsWith("zip")){ filename += ".zip"; }
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME, filename);
      startService(intent);
   }

   private void removeItem(int pos){
      if (mItems == null || mAdapter == null){ return; }

      final TranslationItem selectedItem =
              (TranslationItem)mAdapter.getItem(pos);
      String msg = String.format(getString(R.string.remove_dlg_msg),
              selectedItem.name);
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle(R.string.remove_dlg_title)
             .setMessage(msg)
             .setPositiveButton(R.string.remove_button,
                     new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                           QuranFileUtils.removeTranslation(TranslationManagerActivity.this,
                                   selectedItem.filename);
                           selectedItem.localVersion = null;
                           selectedItem.exists = false;
                           writeDatabaseUpdate(selectedItem);
                           String current = mSharedPreferences.getString(
                                   Constants.PREF_ACTIVE_TRANSLATION, "");
                           if (current.compareTo(selectedItem.filename) == 0){
                              mSharedPreferences.edit().remove(
                                   Constants.PREF_ACTIVE_TRANSLATION).commit();
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

   public boolean hasUpgrade(TranslationItem item){
      return item.latestVersion > -1 && item.localVersion != null &&
              item.latestVersion > item.localVersion;
   }

   private class TranslationsAdapter extends BaseAdapter {
      private LayoutInflater mInflater;
      private List<TranslationItem> mElements;
      private int TYPE_ITEM = 0;
      private int TYPE_SEPARATOR = 1;

      public TranslationsAdapter(Context context,
                                 List<TranslationItem> elements) {
         mInflater = LayoutInflater.from(context);
         mElements = elements;
      }

      public void setData(List<TranslationItem> items){
         mElements = items;
      }

      @Override
      public int getCount() {
         return mElements == null? 0 : mElements.size();
      }

      @Override
      public int getItemViewType(int position){
         return (mElements.get(position).isSeparator)?
                 TYPE_SEPARATOR : TYPE_ITEM;
      }

      @Override
      public int getViewTypeCount() {
         return 2;
      }


      @Override
      public Object getItem(int position) {
         return mElements.get(position);
      }

      @Override
      public long getItemId(int position) {
         return position;
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         ViewHolder holder;

         if (convertView == null){
            holder = new ViewHolder();
            if (getItemViewType(position) == TYPE_ITEM){
               convertView = mInflater.inflate(
                       R.layout.translation_row, parent, false);
               holder.translationTitle = (TextView)convertView
                       .findViewById(R.id.translation_title);
               holder.translationInfo = (TextView)convertView
                       .findViewById(R.id.translation_info);
               holder.leftImage = (ImageView)convertView
                       .findViewById(R.id.left_image);
               holder.rightImage = (ImageView)convertView
                       .findViewById(R.id.right_image);
            }
            else {
               convertView = mInflater.inflate(R.layout.translation_sep, parent, false);
               holder.separatorText = (TextView)convertView
                       .findViewById(R.id.separator_txt);
            }
            convertView.setTag(holder);
         }
         else { holder = (ViewHolder)convertView.getTag(); }

         TranslationItem item = mElements.get(position);
         if (getItemViewType(position) == TYPE_SEPARATOR){
            holder.separatorText.setText(item.name);
         }
         else {
            holder.translationTitle.setText(item.name);
            if (TextUtils.isEmpty(item.translator)){
               holder.translationInfo.setVisibility(View.GONE);
            }
            else {
               holder.translationInfo.setText(item.translator);
               holder.translationInfo.setVisibility(View.VISIBLE);
            }

            if (item.exists){
               if (hasUpgrade(item)){
                  holder.leftImage.setImageResource(R.drawable.ic_download);
                  holder.leftImage.setVisibility(View.VISIBLE);

                  holder.translationInfo.setText(R.string.update_available);
                  holder.translationInfo.setVisibility(View.VISIBLE);
               }
               else { holder.leftImage.setVisibility(View.GONE); }
               holder.rightImage.setImageResource(R.drawable.ic_cancel);
               holder.rightImage.setVisibility(View.VISIBLE);

               final int pos = position;
               holder.rightImage.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                     removeItem(pos);
                  }
               });
            }
            else {
               holder.leftImage.setVisibility(View.GONE);
               holder.rightImage.setImageResource(R.drawable.ic_download);
               holder.rightImage.setVisibility(View.VISIBLE);
               holder.rightImage.setOnClickListener(null);
               holder.rightImage.setClickable(false);
            }
         }

         return convertView;
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
