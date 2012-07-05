package com.quran.labs.androidquran.util;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.DownloadAudioRequest;

import java.io.File;

public class AudioUtils {
   private static final String TAG = "AudioUtils";
   public static final String AUDIO_EXTENSION = ".mp3";

   public final static class LookAheadAmount {
      public static final int PAGE = 1;
      public static final int SURA = 2;
   }

   public static String[] mQariBaseUrls = null;
   public static String[] mQariFilePaths = null;

   public static String getQariUrl(Context context, int position){
      if (mQariBaseUrls == null){
         mQariBaseUrls = context.getResources()
                 .getStringArray(R.array.quran_readers_urls);
      }

      if (position >= mQariBaseUrls.length || 0 > position){ return null; }
      return mQariBaseUrls[position];
   }

   public static String getLocalQariUrl(Context context, int position){
      if (mQariFilePaths == null){
         mQariFilePaths = context.getResources()
                 .getStringArray(R.array.quran_readers_path);
      }

      String rootDirectory = getAudioRootDirectory(context);
      return rootDirectory == null? null :
              rootDirectory + mQariFilePaths[position];
   }

   public static QuranAyah getLastAyahToPlay(QuranAyah startAyah,
                                             int page, int mode){
      if (mode == LookAheadAmount.SURA){
         int sura = startAyah.getSura();
         int lastAyah = QuranInfo.getNumAyahs(sura);
         if (lastAyah == -1){ return null; }
         return new QuranAyah(sura, lastAyah);
      }
      else {
         if (page > 604 || page < 1){ return null; }
         else if (page == 604){ return new QuranAyah(114, 6); }
         else {
            // get sura and ayah for the next page
            int sura = QuranInfo.PAGE_SURA_START[page];
            int ayah = QuranInfo.PAGE_AYAH_START[page];
            return new QuranAyah(sura, ayah-1);
         }
      }
   }

   public static boolean shouldDownloadBasmallah(Context context,
                                                 DownloadAudioRequest request){
      String baseDirectory = request.getLocalPath();
      if (!TextUtils.isEmpty(baseDirectory)){
         File f = new File(baseDirectory);
         if (f.exists()){
            String filename = 1 + File.separator + 1 + AUDIO_EXTENSION;
            f = new File(baseDirectory + File.separator + filename);
            if (f.exists()){
               android.util.Log.d(TAG, "already have basmalla...");
               return false; }
         }
         else {
            f.mkdirs();
         }
      }

      return doesRequireBasmallah(request);
   }

   public static boolean haveSuraAyahForQari(String baseDir, int sura, int ayah){
      String filename = baseDir + File.separator + sura +
              File.separator + ayah + AUDIO_EXTENSION;
      File f = new File(filename);
      return f.exists();
   }

   private static boolean doesRequireBasmallah(AudioRequest request){
      QuranAyah minAyah = request.getMinAyah();
      int startSura = minAyah.getSura();
      int startAyah = minAyah.getAyah();

      QuranAyah maxAyah = request.getMaxAyah();
      int endSura = maxAyah.getSura();
      int endAyah = maxAyah.getAyah();

      android.util.Log.d(TAG, "seeing if need basmalla...");

      for (int i = startSura; i <= endSura; i++){
         int lastAyah = QuranInfo.getNumAyahs(i);
         if (i == endSura){ lastAyah = endAyah; }
         int firstAyah = 1;
         if (i == startSura){ firstAyah = startAyah; }

         for (int j = firstAyah; j < lastAyah; j++){
            if (j == 1 && i != 1 && i != 9){
               android.util.Log.d(TAG, "need basmalla for " + i + ":" + j);

               return true;
            }
         }
      }

      return false;
   }

   public static boolean haveAllFiles(DownloadAudioRequest request){
      String baseDirectory = request.getLocalPath();
      if (TextUtils.isEmpty(baseDirectory)){ return false; }

      File f = new File(baseDirectory);
      if (!f.exists()){
         f.mkdirs();
         return false;
      }

      QuranAyah minAyah = request.getMinAyah();
      int startSura = minAyah.getSura();
      int startAyah = minAyah.getAyah();

      QuranAyah maxAyah = request.getMaxAyah();
      int endSura = maxAyah.getSura();
      int endAyah = maxAyah.getAyah();

      for (int i = startSura; i <= endSura; i++){
         int lastAyah = QuranInfo.getNumAyahs(i);
         if (i == endSura){ lastAyah = endAyah; }
         int firstAyah = 1;
         if (i == startSura){ firstAyah = startAyah; }

         for (int j = firstAyah; j < lastAyah; j++){
            String filename = i + File.separator + j + AUDIO_EXTENSION;
            f = new File(baseDirectory + File.separator + filename);
            if (!f.exists()){ return false; }
         }
      }

      return true;
   }

   public static String getAudioRootDirectory(Context context){
      File f = null;
      String path = "";
      String sep = File.separator;

      if (android.os.Build.VERSION.SDK_INT >= 8){
         f = context.getExternalFilesDir(null);
         path = sep + "ayat" + sep;
      }
      else {
         f = Environment.getExternalStorageDirectory();
         path = sep + "Android" + sep + "data" + sep +
                 context.getPackageName() + sep + "files" + sep + "ayat" + sep;
      }

      return f.getAbsolutePath() + path;
   }
}
