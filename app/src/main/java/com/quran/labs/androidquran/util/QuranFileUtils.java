package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import com.quran.labs.androidquran.data.QuranDataProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class QuranFileUtils {
   private static final String TAG = "QuranFileUtils";

	public static boolean failedToWrite = false;
	public static String IMG_HOST = "http://android.quran.com/data/";
	private static String QURAN_BASE = File.separator + "quran_android"
			+ File.separator;
	private static String DATABASE_DIRECTORY = "databases";
	private static int BUFF_SIZE = 1024;
	public static final String PACKAGE_NAME = "com.quran.labs.androidquran";

	public static boolean debugRmDir(String dir, boolean deleteDirectory) {
		File directory = new File(dir);
		if (directory.isDirectory()) {
			String[] children = directory.list();
			for (String s : children) {
				if (!debugRmDir(dir + File.separator + s, true))
					return false;
			}
		}

		return deleteDirectory ? directory.delete() : true;
	}

	public static void debugLsDir(String dir) {
		File directory = new File(dir);
		Log.d(TAG, directory.getAbsolutePath());

		if (directory.isDirectory()) {
			String[] children = directory.list();
			for (String s : children)
				debugLsDir(dir + File.separator + s);
		}
	}

	public static boolean haveAllImages() {
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED)) {
			File dir = new File(getQuranDirectory() + File.separator);
			if (dir.isDirectory()) {
				int files = dir.list().length;
				if (files >= 604){
               // ideally, we should loop for each page and ensure
               // all pages are there, but this will do for now.
					return true;
            }
			} else { QuranFileUtils.makeQuranDirectory(); }
		}
		return false;
	}
	
	public static String getPageFileName(int p) {
		NumberFormat nf = NumberFormat.getInstance(Locale.US);
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(p) + ".png";
	}
	
	public static boolean isSDCardMounted() {
		String state = Environment.getExternalStorageState();
		return state.equals(Environment.MEDIA_MOUNTED);
	}
	
	public static Bitmap getImageFromSD(String filename){
		String location = getQuranDirectory();
		if (location == null)
			return null;
		
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ALPHA_8;
		return BitmapFactory.decodeFile(location + File.separator + filename, options);
	}

	public static boolean writeNoMediaFile() {
		File f = new File(getQuranDirectory() + "/.nomedia");
		if (f.exists()) {
			return true;
		}

		try {
			return f.createNewFile();
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean makeQuranDirectory() {
		String path = getQuranDirectory();
		if (path == null)
			return false;

		File directory = new File(path);
		if (directory.exists() && directory.isDirectory()) {
			return writeNoMediaFile();
		} else if (directory.mkdirs()) {
			return writeNoMediaFile();
		} else
			return false;
	}

	public static boolean makeQuranDatabaseDirectory() {
		String path = getQuranDatabaseDirectory();
		if (path == null)
			return false;

		File directory = new File(path);
		if (directory.exists() && directory.isDirectory()) {
			return true;
		} else if (directory.mkdirs()) {
			return true;
		} else
			return false;
	}

	public static Bitmap getImageFromWeb(String filename) {
		QuranScreenInfo instance = QuranScreenInfo.getInstance();
		if (instance == null) return null;
		
		String urlString = IMG_HOST + "width"
				+ instance.getWidthParam() + "/"
				+ filename;
		Log.d(TAG, "want to download: " + urlString);

		InputStream is;
		try {
			URL url = new URL(urlString);
			is = (InputStream) url.getContent();
		} catch (Exception e) {
			return null;
		}

		if (failedToWrite)
			return BitmapFactory.decodeStream(is);

		String path = getQuranDirectory();
		if (path != null) {
			path += File.separator + filename;

			if (!QuranFileUtils.makeQuranDirectory()) {
				failedToWrite = true;
				return BitmapFactory.decodeStream(is);
			}

			boolean readPhase = false;
			try {
				readPhase = true;
				saveStream(is, path);

				return QuranFileUtils.getImageFromSD(filename);
			} catch (Exception e) {
				Log.d("quran_utils", e.toString());
				if (readPhase == false)
					return BitmapFactory.decodeStream(is);
				failedToWrite = true;
				return QuranFileUtils.getImageFromWeb(filename);
			}
		} else
			return BitmapFactory.decodeStream(is);
	}

	private static void saveStream(InputStream is, String savePath)
			throws IOException {
		FileOutputStream output = new FileOutputStream(savePath);
		int readlen;

		byte[] buf = new byte[BUFF_SIZE];
		while ((readlen = is.read(buf)) > 0)
			output.write(buf, 0, readlen);
		output.close();
		is.close();
	}

	public static String getQuranBaseDirectory() {
		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED))
			return Environment.getExternalStorageDirectory() + QURAN_BASE;
		else
			return null;
	}

	public static String getQuranDatabaseDirectory() {
		String base = getQuranBaseDirectory();
		return (base == null) ? null : base + DATABASE_DIRECTORY;
	}

	public static String getQuranDirectory() {
		String base = getQuranBaseDirectory();
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null)
			return null;
		return (base == null) ? null : base + "width" + qsi.getWidthParam();
	}

	public static String getZipFileUrl() {
		String url = IMG_HOST;
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null)
			return null;
		url += "images" + qsi.getWidthParam() + ".zip";
		return url;
	}
	
	public static String getAyaPositionFileName(){
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null) return null;
		return "ayahinfo" + qsi.getWidthParam() + ".db";
	}

	public static String getAyaPositionFileUrl() {
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null)
			return null;
		String url = IMG_HOST + "width" + qsi.getWidthParam();
		url += "/ayahinfo" + qsi.getWidthParam() + ".zip";
		return url;
	}

   public static String getGaplessDatabaseRootUrl() {
      QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      if (qsi == null)
         return null;
      return IMG_HOST + "databases/audio/";
   }

	public static boolean haveAyaPositionFile(){
		String base = QuranFileUtils.getQuranDatabaseDirectory();
		if (base == null)
			QuranFileUtils.makeQuranDatabaseDirectory();
		String filename = QuranFileUtils.getAyaPositionFileName();
		if (filename != null){
			String ayaPositionDb = base + File.separator + filename;
			File f = new File(ayaPositionDb);
			if (!f.exists()) {
				return false;
			}
			else { return true; }
		}

		return false;
	}

	public static boolean hasTranslation(String fileName) {
		String path = getQuranDatabaseDirectory();
		if (path != null) {
			path += File.separator + fileName;
			return new File(path).exists();
		}
		return false;
	}
	
	public static boolean removeTranslation(String fileName) {
		String path = getQuranDatabaseDirectory();
		if (path != null) {
			path += File.separator + fileName;
			File f = new File(path);
			return f.delete();
		}
		return false;
	}

   public static boolean hasArabicSearchDatabase(){
      return hasTranslation(QuranDataProvider.QURAN_ARABIC_DATABASE);
   }

   public static String getArabicSearchDatabaseUrl(){
      return IMG_HOST + DATABASE_DIRECTORY + "/" +
              QuranDataProvider.QURAN_ARABIC_DATABASE;
   }

   public static void migrateAudio(Context context){
      String oldAudioDirectory = AudioUtils.getOldAudioRootDirectory(context);
      String destinationAudioDirectory = AudioUtils.getAudioRootDirectory();
      if (oldAudioDirectory != null && destinationAudioDirectory != null){
         File old = new File(oldAudioDirectory);
         if (old.exists()){
            Log.d(TAG, "old audio path exists");
            File dest = new File(destinationAudioDirectory);
            if (!dest.exists()){
               // just in case the user manually deleted /sdcard/quran_android
               // and left the audio as is (unlikely, but just in case).
               String parentDir = QuranFileUtils.getQuranBaseDirectory();
               new File(parentDir).mkdir();

               Log.d(TAG, "new audio path doesn't exist, renaming...");
               boolean result = old.renameTo(dest);
               Log.d(TAG, "result of renaming: " + result);
            }
            else {
               Log.d(TAG, "destination already exists..");
               File[] oldFiles = old.listFiles();
               if (oldFiles != null){
                  for (File f : oldFiles){
                     File newFile = new File(dest, f.getName());
                     if (newFile != null){
                        boolean result = f.renameTo(newFile);
                        Log.d(TAG, "attempting to copy " + f +
                                " to " + newFile + ", res: " + result);
                     }
                  }
               }
            }
         }

         try {
            // make the .nomedia file if it doesn't already exist
            File noMediaFile = new File(destinationAudioDirectory, ".nomedia");
            if (!noMediaFile.exists()){
               noMediaFile.createNewFile();
            }
         }
         catch (IOException ioe){
         }
      }
   }

}
