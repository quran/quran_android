package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import com.quran.labs.androidquran.data.QuranDataProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class QuranFileUtils {
   private static final String TAG = "QuranFileUtils";

	public static boolean failedToWrite = false;
	public static String IMG_HOST = "http://android.quran.com/data/";
	private static String QURAN_BASE = "quran_android/";
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

   // check if the images with the given width param have a version
   // that we specify (ex if version is 3, check for a .v3 file).
   public static boolean isVersion(Context context,
                                   String widthParam, int version){
      String quranDirectory = getQuranDirectory(context, widthParam);
      if (quranDirectory == null){ return false; }
      try {
         File vFile = new File(quranDirectory +
                 File.separator + ".v" + version);
         return vFile.exists();
      }
      catch (Exception e){
         return false;
      }
   }

   public static boolean haveAllImages(Context context, String widthParam){
      String quranDirectory = getQuranDirectory(context, widthParam);
      if (quranDirectory == null){ return false; }

		String state = Environment.getExternalStorageState();
		if (state.equals(Environment.MEDIA_MOUNTED)) {
			File dir = new File(quranDirectory + File.separator);
			if (dir.isDirectory()) {
            String[] fileList = dir.list();
            if (fileList == null){ return false; }
				int files = fileList.length;
				if (files >= 604){
               // ideally, we should loop for each page and ensure
               // all pages are there, but this will do for now.
					return true;
            }
			} else { QuranFileUtils.makeQuranDirectory(context); }
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
   public static Bitmap getImageFromSD(Context context, String filename){
      return getImageFromSD(context, null, filename);
   }

	public static Bitmap getImageFromSD(Context context, String widthParam,
                                       String filename){
		String location;
      if (widthParam != null){
         location = getQuranDirectory(context, widthParam);
      }
      else { location = getQuranDirectory(context); }

		if (location == null){
			return null;
      }
		
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ALPHA_8;
		return BitmapFactory.decodeFile(location +
              File.separator + filename, options);
	}

	public static boolean writeNoMediaFile(Context context) {
		File f = new File(getQuranDirectory(context) + "/.nomedia");
		if (f.exists()) {
			return true;
		}

		try {
			return f.createNewFile();
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean makeQuranDirectory(Context context) {
		String path = getQuranDirectory(context);
		if (path == null)
			return false;

		File directory = new File(path);
		if (directory.exists() && directory.isDirectory()) {
			return writeNoMediaFile(context);
		} else if (directory.mkdirs()) {
			return writeNoMediaFile(context);
		} else
			return false;
	}

	public static boolean makeQuranDatabaseDirectory(Context context) {
		String path = getQuranDatabaseDirectory(context);
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

	public static Bitmap getImageFromWeb(Context context, String filename) {
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

		String path = getQuranDirectory(context);
		if (path != null) {
			path += File.separator + filename;

			if (!QuranFileUtils.makeQuranDirectory(context)) {
				failedToWrite = true;
				return BitmapFactory.decodeStream(is);
			}

			boolean readPhase = false;
			try {
				readPhase = true;
				saveStream(is, path);

				return QuranFileUtils.getImageFromSD(context, filename);
			} catch (Exception e) {
				Log.d("quran_utils", e.toString());
				if (readPhase == false)
					return BitmapFactory.decodeStream(is);
				failedToWrite = true;
				return QuranFileUtils.getImageFromWeb(context, filename);
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

	public static String getQuranBaseDirectory(Context context) {
        String basePath = null;
        if (QuranSettings.useCustomLocation(context)) {
            basePath = QuranSettings.getAppCustomLocation(context);
        } else if (isSDCardMounted()) {
            basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }

        if (basePath != null) {
            if (!basePath.endsWith("/"))
                basePath += "/";
            return basePath + QURAN_BASE;
        }
        return null;
	}

    /**
     * Returns the app used space in megabytes
     * @return
     */
    public static int getAppUsedSpace(Context context) {
        File base = new File(getQuranBaseDirectory(context));
        ArrayList<File> files = new ArrayList<File>();
        files.add(base);
        long size = 0;
        while (!files.isEmpty()) {
            File f = files.remove(0);
            if (f.isDirectory()) {
                File[] subFiles = f.listFiles();
                for (File sf: subFiles) files.add(sf);
            } else {
                size += f.length();
            }
        }
        return (int) (size / (long) (1024 * 1024));
    }

	public static String getQuranDatabaseDirectory(Context context) {
		String base = getQuranBaseDirectory(context);
		return (base == null) ? null : base + DATABASE_DIRECTORY;
	}

	public static String getQuranDirectory(Context context) {
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null){
			return null;
      }
      return getQuranDirectory(context, qsi.getWidthParam());
	}

   public static String getQuranDirectory(Context context, String widthParam){
      String base = getQuranBaseDirectory(context);
      return (base == null) ? null : base + "width" + widthParam;
   }

   public static String getZipFileUrl() {
      QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      if (qsi == null){ return null; }
      return getZipFileUrl(qsi.getWidthParam());
   }

   public static String getZipFileUrl(String widthParam) {
		String url = IMG_HOST;
		url += "images" + widthParam + ".zip";
		return url;
	}

   public static String getPatchFileUrl(String widthParam, int toVersion){
      return IMG_HOST + "patches/patch" +
              widthParam + "_v" + toVersion + ".zip";
   }
	
	public static String getAyaPositionFileName(){
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null) return null;
		return getAyaPositionFileName(qsi.getWidthParam());
	}

   public static String getAyaPositionFileName(String widthParam){
      return "ayahinfo" + widthParam + ".db";
   }

	public static String getAyaPositionFileUrl() {
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
		if (qsi == null){ return null; }
      return getAyaPositionFileUrl(qsi.getWidthParam());
	}

   public static String getAyaPositionFileUrl(String widthParam) {
      String url = IMG_HOST + "width" + widthParam;
      url += "/ayahinfo" + widthParam + ".zip";
      return url;
   }

   public static String getGaplessDatabaseRootUrl() {
      QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      if (qsi == null)
         return null;
      return IMG_HOST + "databases/audio/";
   }

	public static boolean haveAyaPositionFile(Context context){
		String base = QuranFileUtils.getQuranDatabaseDirectory(context);
		if (base == null)
			QuranFileUtils.makeQuranDatabaseDirectory(context);
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

	public static boolean hasTranslation(Context context, String fileName) {
		String path = getQuranDatabaseDirectory(context);
		if (path != null) {
			path += File.separator + fileName;
			return new File(path).exists();
		}
		return false;
	}
	
	public static boolean removeTranslation(Context context, String fileName) {
		String path = getQuranDatabaseDirectory(context);
		if (path != null) {
			path += File.separator + fileName;
			File f = new File(path);
			return f.delete();
		}
		return false;
	}

   public static boolean hasArabicSearchDatabase(Context context){
      return hasTranslation(context, QuranDataProvider.QURAN_ARABIC_DATABASE);
   }

   public static String getArabicSearchDatabaseUrl(){
      return IMG_HOST + DATABASE_DIRECTORY + "/" +
              QuranDataProvider.QURAN_ARABIC_DATABASE;
   }

   public static void migrateAudio(Context context){
      String oldAudioDirectory = AudioUtils.getOldAudioRootDirectory(context);
      String destinationAudioDirectory = AudioUtils.getAudioRootDirectory(context);
      if (oldAudioDirectory != null && destinationAudioDirectory != null){
         File old = new File(oldAudioDirectory);
         if (old.exists()){
            Log.d(TAG, "old audio path exists");
            File dest = new File(destinationAudioDirectory);
            if (!dest.exists()){
               // just in case the user manually deleted /sdcard/quran_android
               // and left the audio as is (unlikely, but just in case).
               String parentDir = QuranFileUtils.getQuranBaseDirectory(context);
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

    public static boolean moveAppFiles(Context context, String newLocation) {
        if (QuranSettings.getAppCustomLocation(context).equals(newLocation))
            return true;
        File currentDirectory = new File(getQuranBaseDirectory(context));
        File newDirectory = new File(newLocation, QURAN_BASE);
        if (!currentDirectory.exists()) {
            // No files to copy, so change the app directory directly
            return true;
        } else if (newDirectory.exists() || newDirectory.mkdirs()) {
            try {
                copyFileOrDirectory(currentDirectory, newDirectory);
                deleteFileOrDirectory(currentDirectory);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "error moving app files", e);
            }
        }
        return false;
    }

    private static void deleteFileOrDirectory(File file) {
        if (file.isDirectory()) {
            File[] subFiles = file.listFiles();
            for (File sf: subFiles) {
                if (sf.isFile())
                    sf.delete();
                else
                    deleteFileOrDirectory(sf);
            }
        }
        file.delete();
    }

    private static void copyFileOrDirectory(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }

            File[] files = source.listFiles();
            for (File f: files) {
                copyFileOrDirectory(f, new File(destination, f.getName()));
            }
        } else {
            copyFile(source, destination);
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(destination);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        out.flush();
        out.close();
        in.close();
    }
}
