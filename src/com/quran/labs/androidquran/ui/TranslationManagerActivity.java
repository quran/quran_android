package com.quran.labs.androidquran.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.util.QuranFileUtils;

public class TranslationManagerActivity extends SherlockActivity
        implements DefaultDownloadReceiver.SimpleDownloadListener {
   public static final String TAG = "TranslationManager";
   public static final String TRANSLATION_DOWNLOAD_KEY =
           "TRANSLATION_DOWNLOAD_KEY";

   public static final String WEB_SERVICE_URL =
           "http://labs.quran.com/androidquran/translations.php";

   private List<TranslationItem> mItems;
   private List<TranslationItem> mAllItems;

   private ListView mListView;
   private TextView mMessageArea;
   private TranslationsAdapter mAdapter;
   private SharedPreferences mPrefs;

   private String mActiveTranslation;
   private int mDownloadedTranslations;

   private DefaultDownloadReceiver mDownloadReceiver = null;

   @Override
   public void onCreate(Bundle savedInstanceState){
      setTheme(R.style.Theme_Sherlock);
      super.onCreate(savedInstanceState);
      requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

      setContentView(R.layout.translation_manager);
      mListView = (ListView)findViewById(R.id.translation_list);
      mAdapter = new TranslationsAdapter(this, null);
      mListView.setAdapter(mAdapter);
      mMessageArea = (TextView)findViewById(R.id.message_area);

      setSupportProgressBarIndeterminateVisibility(true);
      mPrefs = PreferenceManager.getDefaultSharedPreferences(
              getApplicationContext());
      new LoadTranslationsTask().execute();
   }

   @Override
   public void onPause(){
      if (mDownloadReceiver != null){
         mDownloadReceiver.setListener(null);
         LocalBroadcastManager.getInstance(this)
              .unregisterReceiver(mDownloadReceiver);
         mDownloadReceiver = null;
      }
      super.onPause();
   }

   @Override
   public void handleDownloadSuccess(){
      generateListItems();
   }

   @Override
   public void handleDownloadFailure(int errId){
   }

   private class LoadTranslationsTask extends
           AsyncTask<Void, Void, List<TranslationItem>> {

      @Override
      public List<TranslationItem> doInBackground(Void... params) {
         String text = downloadUrl(WEB_SERVICE_URL);
         if (text == null){ return null; }

         List<TranslationItem> items = new ArrayList<TranslationItem>();
         try {
            JSONObject data = (JSONObject)new JSONTokener(text).nextValue();
            JSONArray translations = data.getJSONArray("data");
            int length = translations.length();
            for (int i = 0; i < length; i++){
               JSONObject translation = translations.getJSONObject(i);
               int id = Integer.parseInt(translation.getString("id"));
               String name = translation.getString("displayName");
               String who = translation.getString("translator_foreign");
               if (TextUtils.isEmpty(who) || "null".equals(who)){
                  who = translation.getString("translator");
                  if (!TextUtils.isEmpty(who) && "null".equals(who)){
                     who = null;
                  }
               }
               String url = translation.getString("fileUrl");
               String filename = translation.getString("fileName");

               int firstParen = name.indexOf("(");
               if (firstParen != -1){
                  name = name.substring(0, firstParen-1);
               }

               TranslationItem item = new TranslationItem(
                       id, name, who, filename, url, false);
               items.add(item);
            }
         }
         catch (JSONException je){
            Log.d(TAG, "error parsing json: " + je);
         }

         return items;
      }

      @Override
      public void onPostExecute(List<TranslationItem> items){
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
      }
   }

   private void generateListItems(){
      mActiveTranslation = mPrefs.getString(
              ApplicationConstants.PREF_ACTIVE_TRANSLATION, null);
      if (mAllItems == null){ return; }

      List<TranslationItem> downloaded = new ArrayList<TranslationItem>();
      List<TranslationItem> notDownloaded =
              new ArrayList<TranslationItem>();

      String databaseDir = QuranFileUtils.getQuranDatabaseDirectory();
      for (TranslationItem item : mAllItems){
         String path = databaseDir + File.separator + item.filename;
         item.exists = new File(path).exists();

         if (item.exists){ downloaded.add(item); }
         else { notDownloaded.add(item); }
      }

      List<TranslationItem> res = new ArrayList<TranslationItem>();
      TranslationItem hdr = new TranslationItem(
              getString(R.string.downloaded_translations));
      hdr.isSeparator = true;
      res.add(hdr);

      mDownloadedTranslations = downloaded.size();
      for (TranslationItem item : downloaded){
         if (mActiveTranslation != null &&
                 mActiveTranslation.equals(item.filename)){
            item.active = true;
         }
         else { item.active = false; }
         res.add(item);
      }

      if (mDownloadedTranslations > 0 && mActiveTranslation == null){
         res.get(1).active = true;
         mActiveTranslation = res.get(1).filename;
         mPrefs.edit().putString(ApplicationConstants.PREF_ACTIVE_TRANSLATION,
                 mActiveTranslation).commit();
      }

      hdr = new TranslationItem(getString(R.string.available_translations));
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
      String destination = QuranFileUtils.getQuranDatabaseDirectory();
      Log.d(TAG, "downloading " + url + " to " + destination);
      // start the download
      String notificationTitle = selectedItem.name;
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
              destination, notificationTitle, TRANSLATION_DOWNLOAD_KEY,
              QuranDownloadService.DOWNLOAD_TYPE_TRANSLATION);
      intent.putExtra(QuranDownloadService.EXTRA_OUTPUT_FILE_NAME,
              selectedItem.filename);
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
                           if (selectedItem.active){
                              mActiveTranslation = null;
                              mPrefs.edit().remove(
                                      ApplicationConstants
                                              .PREF_ACTIVE_TRANSLATION)
                                      .commit();
                           }
                           QuranFileUtils.removeTranslation(
                                   selectedItem.filename);
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

   private void activateTranslation(int pos){
      if (mItems == null || mAdapter == null){ return; }

      final TranslationItem selectedItem =
              (TranslationItem)mAdapter.getItem(pos);
      if (mActiveTranslation != null){
         for (int i=0; i<mDownloadedTranslations; i++){
            TranslationItem item = mItems.get(i);
            if (item.active){
               item.active = false;
            }
         }
      }

      selectedItem.active = true;
      mPrefs.edit().putString(ApplicationConstants.PREF_ACTIVE_TRANSLATION,
              selectedItem.filename).commit();
      mActiveTranslation = selectedItem.filename;
      generateListItems();
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
                       R.layout.translation_row, null);
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
               convertView = mInflater.inflate(R.layout.translation_sep, null);
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
               if (item.active){
                  holder.leftImage.setImageResource(R.drawable.favorite);
               }
               else {
                  holder.leftImage.setImageResource(R.drawable.not_favorite);

                  final int pos = position;
                  holder.leftImage.setOnClickListener(new View.OnClickListener() {
                     @Override
                     public void onClick(View view) {
                        activateTranslation(pos);
                     }
                  });
               }

               holder.leftImage.setVisibility(View.VISIBLE);
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
               final int pos = position;
               holder.leftImage.setVisibility(View.GONE);
               holder.rightImage.setImageResource(R.drawable.ic_download);
               holder.rightImage.setVisibility(View.VISIBLE);
               holder.rightImage.setOnClickListener(new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                     downloadItem(pos);
                  }
               });
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

   public static class TranslationItem {
      public int id;
      public String name;
      public String translator;
      public String filename;
      public String url;
      public boolean active;
      public boolean exists;
      public boolean isSeparator = false;

      public TranslationItem(String name){
         this.name = name;
      }

      public TranslationItem(int id, String name, String translator,
                             String filename, String url, boolean exists){
         this.id = id;
         this.name = name;
         this.translator = translator;
         this.filename = filename;
         this.url = url;
         this.exists = false;
      }
   }

   private String downloadUrl(String urlString){
      InputStream stream = null;
      try {
         URL url = new URL(urlString);
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setReadTimeout(10000);
         conn.setConnectTimeout(15000);
         conn.setDoInput(true);

         conn.connect();
         stream = conn.getInputStream();

         String result = "";
         BufferedReader reader =
                 new BufferedReader(new InputStreamReader(stream, "UTF-8"));

         String line = "";
         while ((line = reader.readLine()) != null){
            result += line;
         }

         try { reader.close(); }
         catch (Exception e){ }

         return result;
      }
      catch (Exception e){
         Log.d(TAG, "error downloading translation data: " + e);
      }

      return null;
   }
}
