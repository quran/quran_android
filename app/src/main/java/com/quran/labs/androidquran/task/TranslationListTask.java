package com.quran.labs.androidquran.task;

import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class TranslationListTask extends
        AsyncTask<Void, Void, List<TranslationItem>> {

   public static final String WEB_SERVICE_URL =
           "http://android.quran.com/data/translations.php?v=2";
   private static final String CACHED_RESPONSE_FILE_NAME =
           "cached-translation-list";

   private Context mContext;
   private TranslationsUpdatedListener mListener;

   public TranslationListTask(Context context,
                              TranslationsUpdatedListener listener){
      mContext = context.getApplicationContext();
      mListener = listener;
   }

   @Override
   public List<TranslationItem> doInBackground(Void... params) {
      return downloadTranslations(mContext, true);
   }

   @Override
   public void onPostExecute(List<TranslationItem> items){
      if (mListener != null){
         mListener.translationsUpdated(items);
      }
      mListener = null;
   }

   private static void cacheResponse(Context context, String response) {
      try {
         PrintWriter pw = new PrintWriter(getCachedResponseFilePath(context));
         pw.write(response);
         pw.close();
      } catch (Exception e) {
         Timber.e("failed to cache response",e);
      }
   }

   private static String loadCachedResponse(Context context) {
      String response = null;
      try {
         final File cachedFile = getCachedResponseFilePath(context);
         if (cachedFile.exists()) {
            FileReader fr = new FileReader(cachedFile);
            BufferedReader br = new BufferedReader(fr);
            response = "";
            String line;
            while ((line = br.readLine()) != null) {
               response += line + "\n";
            }
            br.close();
         }
      } catch (Exception e) {
         Timber.e("failed reading cached response",e);
      }
      return response;
   }

   public static List<TranslationItem> downloadTranslations(Context context,
                                                            boolean useCache){
      boolean shouldUseCache = false;
      if (useCache){
         long when = QuranSettings.getInstance(context).getLastUpdatedTranslationDate();
         if (System.currentTimeMillis() - when < Constants.MIN_TRANSLATION_REFRESH_TIME){
            shouldUseCache = true;
         }
      }

      String text = null;
      if (shouldUseCache){
         text = loadCachedResponse(context);
      }

      boolean refreshed = false;
      if (TextUtils.isEmpty(text)){
         text = downloadUrl(WEB_SERVICE_URL);
         if (TextUtils.isEmpty(text)){ return null; }
         if (useCache){ cacheResponse(context, text); }
         refreshed = true;
      }

      SparseArray<TranslationItem> cachedItems;
      TranslationsDBAdapter adapter =
              new TranslationsDBAdapter(context);
      cachedItems = adapter.getTranslationsHash();
      if (cachedItems == null){
         cachedItems = new SparseArray<>();
      }

      List<TranslationItem> items = new ArrayList<TranslationItem>();
      List<TranslationItem> updates = new ArrayList<TranslationItem>();

      try {
         Object responseItem = new JSONTokener(text).nextValue();
         if (!(responseItem instanceof JSONObject)){ return null; }
         JSONObject data = (JSONObject)responseItem;
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
            int version = -1;
            try {
               version = Integer.parseInt(
                       translation.getString("current_version"));
            }
            catch (Exception e){
               // this could happen if we have an older cached translation list
            }

            int firstParen = name.indexOf("(");
            if (firstParen != -1){
               name = name.substring(0, firstParen-1);
            }

            String databaseDir = QuranFileUtils.getQuranDatabaseDirectory(context);
            String path = databaseDir + File.separator + filename;
            boolean exists = new File(path).exists();

            boolean needsUpdate = false;
            TranslationItem item = new TranslationItem(
                    id, name, who, version, filename, url, exists);
            if (exists){
               TranslationItem localItem = cachedItems.get(id);
               if (localItem != null){
                  item.localVersion = localItem.localVersion;
               }
               else if (version > -1) {
                  needsUpdate = true;
                  try {
                     DatabaseHandler mHandler =
                         DatabaseHandler.getDatabaseHandler(context, filename);
                     if (mHandler.validDatabase()){
                        item.localVersion = mHandler.getTextVersion();
                     }
                  }
                  catch (Exception e){
                     Timber.d("exception opening database: " + name,e);
                  }
               }
               else { needsUpdate = true; }
            }
            else if (cachedItems.get(id) != null){
               needsUpdate = true;
            }

            if (needsUpdate){
               updates.add(item);
            }

            if (item.exists){
               Timber.d("found: " + name + " with " +
                       item.localVersion + " vs server's " +
                       item.latestVersion);
            }
            items.add(item);
         }

         if (refreshed){
            QuranSettings.getInstance(context)
                .setLastUpdatedTranslationDate(System.currentTimeMillis());
         }

         if (updates.size() > 0){
            adapter.writeTranslationUpdates(updates);
         }
         adapter.close();
      }
      catch (JSONException je){
         Timber.d("error parsing json: " + je);
      }

      return items;
   }

   private static File getCachedResponseFilePath(Context context) {
      String fileName = CACHED_RESPONSE_FILE_NAME;
      String dir = QuranFileUtils.getQuranDatabaseDirectory(context);
      return new File(dir + File.separator + fileName);
   }

   private static String downloadUrl(String urlString){
      InputStream stream;
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

         String line;
         while ((line = reader.readLine()) != null){
            result += line;
         }

         try { reader.close(); }
         catch (Exception e){
            // no op
         }

         return result;
      }
      catch (Exception e){
         Timber.d("error downloading translation data: " + e);
      }

      return null;
   }

   public interface TranslationsUpdatedListener {
      void translationsUpdated(List<TranslationItem> items);
   }
}
