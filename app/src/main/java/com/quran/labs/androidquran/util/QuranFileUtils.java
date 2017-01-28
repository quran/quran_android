package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranFileConstants;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import timber.log.Timber;

import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;

public class QuranFileUtils {

  // server urls
  private static final String IMG_BASE_URL = QuranFileConstants.IMG_BASE_URL;
  private static final String IMG_ZIP_BASE_URL = QuranFileConstants.IMG_ZIP_BASE_URL;
  private static final String PATCH_ZIP_BASE_URL = QuranFileConstants.PATCH_ZIP_BASE_URL;
  private static final String DATABASE_BASE_URL = QuranFileConstants.DATABASE_BASE_URL;
  private static final String AYAHINFO_BASE_URL = QuranFileConstants.AYAHINFO_BASE_URL;
  private static final String AUDIO_DB_BASE_URL = QuranFileConstants.AUDIO_DB_BASE_URL;

  // local paths
  private static final String QURAN_BASE = QuranFileConstants.QURAN_BASE;
  private static final String DATABASE_DIRECTORY = QuranFileConstants.DATABASE_DIRECTORY;
  private static final String AUDIO_DIRECTORY = QuranFileConstants.AUDIO_DIRECTORY;
  private static final String AYAHINFO_DIRECTORY = QuranFileConstants.AYAHINFO_DIRECTORY;
  private static final String IMAGES_DIRECTORY = QuranFileConstants.IMAGES_DIRECTORY;

  // check if the images with the given width param have a version
  // that we specify (ex if version is 3, check for a .v3 file).
  public static boolean isVersion(Context context, String widthParam, int version) {
    String quranDirectory = getQuranImagesDirectory(context, widthParam);
    Timber.d("isVersion: checking if version %d exists for width %s at %s",
        version, widthParam, quranDirectory);
    if (quranDirectory == null) {
      return false;
    }

    // version 1 or below are true as long as you have images
    if (version <= 1) {
      return true;
    }

    // check the version code
    try {
      File vFile = new File(quranDirectory +
          File.separator + ".v" + version);
      return vFile.exists();
    } catch (Exception e) {
      Timber.e(e, "isVersion: exception while checking version file");
      return false;
    }
  }

  public static String getPotentialFallbackDirectory(Context context) {
    final String state = Environment.getExternalStorageState();
    if (state.equals(Environment.MEDIA_MOUNTED)) {
      if (haveAllImages(context, "_1920")) {
        return "1920";
      } else if (haveAllImages(context, "_1280")) {
        return "1280";
      } else if (haveAllImages(context, "_1024")) {
        return "1024";
      } else {
        return "";
      }
    }
    return null;
  }

  public static boolean haveAllImages(Context context, String widthParam) {
    String quranDirectory = getQuranImagesDirectory(context, widthParam);
    Timber.d("haveAllImages: for width %s, directory is: %s", widthParam, quranDirectory);
    if (quranDirectory == null) {
      return false;
    }

    String state = Environment.getExternalStorageState();
    if (state.equals(Environment.MEDIA_MOUNTED)) {
      File dir = new File(quranDirectory + File.separator);
      if (dir.isDirectory()) {
        Timber.d("haveAllImages: media state is mounted and directory exists");
        String[] fileList = dir.list();
        if (fileList == null) {
          Timber.d("haveAllImages: null fileList, checking page by page...");
          for (int i = 1; i <= PAGES_LAST; i++) {
            if (!new File(dir, getPageFileName(i)).exists()) {
              Timber.d("haveAllImages: couldn't find page %d", i);
              return false;
            }
          }
        } else if (fileList.length < PAGES_LAST) {
          // ideally, we should loop for each page and ensure
          // all pages are there, but this will do for now.
          Timber.d("haveAllImages: found %d files instead of 604.", fileList.length);
          return false;
        }
        return true;
      } else {
        Timber.d("haveAllImages: couldn't find the directory, so making it instead");
        QuranFileUtils.makeQuranDirectory(context);
        if (!IMAGES_DIRECTORY.isEmpty()) {
          QuranFileUtils.makeQuranImagesDirectory(context);
        }
      }
    }
    return false;
  }

  public static String getPageFileName(int p) {
    NumberFormat nf = NumberFormat.getInstance(Locale.US);
    nf.setMinimumIntegerDigits(3);
    return "page" + nf.format(p) + ".png";
  }

  private static boolean isSDCardMounted() {
    String state = Environment.getExternalStorageState();
    return state.equals(Environment.MEDIA_MOUNTED);
  }

  @NonNull
  public static Response getImageFromSD(Context context, String widthParam, String filename) {
    String location;
    if (widthParam != null) {
      location = getQuranImagesDirectory(context, widthParam);
    } else {
      location = getQuranImagesDirectory(context);
    }

    if (location == null) {
      return new Response(Response.ERROR_SD_CARD_NOT_FOUND);
    }

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
    final Bitmap bitmap = BitmapFactory.decodeFile(location +
        File.separator + filename, options);
    return bitmap == null ? new Response(Response.ERROR_FILE_NOT_FOUND) : new Response(bitmap);
  }

  private static boolean writeNoMediaFile(String parentDir) {
    File f = new File(parentDir + "/.nomedia");
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
    String path = getQuranImagesDirectory(context);
    if (path == null) {
      return false;
    }

    File directory = new File(path);
    if (directory.exists() && directory.isDirectory()) {
      return writeNoMediaFile(path);
    } else {
      return directory.mkdirs() && writeNoMediaFile(path);
    }
  }

  private static boolean makeQuranImagesDirectory(Context context) {
    return makeDirectory(getQuranImagesDirectory(context));
  }

  private static boolean makeDirectory(String path) {
    if (path == null) {
      return false;
    }

    File directory = new File(path);
    return directory.exists() && directory.isDirectory() || directory.mkdirs();
  }

  private static boolean makeQuranDatabaseDirectory(Context context) {
    return makeDirectory(getQuranDatabaseDirectory(context));
  }

  private static boolean makeQuranAyahDatabaseDirectory(Context context) {
    return makeQuranDatabaseDirectory(context) &&
        makeDirectory(getQuranAyahDatabaseDirectory(context));
  }

  public static Response getImageFromWeb(OkHttpClient okHttpClient,
      Context context, String filename) {
    return getImageFromWeb(okHttpClient, context, filename, false);
  }

  @NonNull
  private static Response getImageFromWeb(OkHttpClient okHttpClient,
      Context context, String filename, boolean isRetry) {
    QuranScreenInfo instance = QuranScreenInfo.getInstance();
    if (instance == null) {
      instance = QuranScreenInfo.getOrMakeInstance(context);
    }

    String urlString = IMG_BASE_URL + "width"
        + instance.getWidthParam() + File.separator
        + filename;
    Timber.d("want to download: %s", urlString);

    final Request request = new Request.Builder()
        .url(urlString)
        .build();
    final Call call = okHttpClient.newCall(request);

    InputStream stream = null;
    try {
      final okhttp3.Response response = call.execute();
      if (response.isSuccessful()) {
        stream = response.body().byteStream();
        final Bitmap bitmap = decodeBitmapStream(stream);
        if (bitmap != null) {
          String path = getQuranImagesDirectory(context);
          int warning = Response.WARN_SD_CARD_NOT_FOUND;
          if (path != null && QuranFileUtils.makeQuranDirectory(context)) {
            path += File.separator + filename;
            warning = tryToSaveBitmap(bitmap, path) ? 0 : Response.WARN_COULD_NOT_SAVE_FILE;
          }
          return new Response(bitmap, warning);
        }
      }
    } catch (IOException ioe) {
      Timber.e(ioe, "exception downloading file");
    } finally {
      closeQuietly(stream);
    }

    return isRetry ? new Response(Response.ERROR_DOWNLOADING_ERROR) :
        getImageFromWeb(okHttpClient, context, filename, true);
  }

  private static Bitmap decodeBitmapStream(InputStream is) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
    return BitmapFactory.decodeStream(is, null, options);
  }

  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        // no op
      }
    }
  }

  private static boolean tryToSaveBitmap(Bitmap bitmap, String savePath) {
    FileOutputStream output = null;
    try {
      output = new FileOutputStream(savePath);
      return bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    } catch (IOException ioe) {
      // do nothing
    } finally {
      try {
        if (output != null) {
          output.flush();
          output.close();
        }
      } catch (Exception e) {
        // ignore...
      }
    }
    return false;
  }

  @Nullable
  public static String getQuranBaseDirectory(Context context) {
    String basePath = QuranSettings.getInstance(context).getAppCustomLocation();

    if (!isSDCardMounted()) {
      // if our best guess suggests that we won't have access to the data due to the sdcard not
      // being mounted, then set the base path to null for now.
      if (basePath == null || basePath.equals(
          Environment.getExternalStorageDirectory().getAbsolutePath()) ||
          (basePath.contains(BuildConfig.APPLICATION_ID) && context.getExternalFilesDir(null) == null)) {
        basePath = null;
      }
    }

    if (basePath != null) {
      if (!basePath.endsWith(File.separator)) {
        basePath += File.separator;
      }
      return basePath + QURAN_BASE;
    }
    return null;
  }

  /**
   * Returns the app used space in megabytes
   */
  public static int getAppUsedSpace(Context context) {
    final String baseDirectory = getQuranBaseDirectory(context);
    if (baseDirectory == null) {
      return -1;
    }

    File base = new File(baseDirectory);
    ArrayList<File> files = new ArrayList<>();
    files.add(base);
    long size = 0;
    while (!files.isEmpty()) {
      File f = files.remove(0);
      if (f.isDirectory()) {
        File[] subFiles = f.listFiles();
        if (subFiles != null) {
          Collections.addAll(files, subFiles);
        }
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

  public static String getQuranAyahDatabaseDirectory(Context context) {
    String base = getQuranBaseDirectory(context);
    return base == null ? null : base + File.separator + AYAHINFO_DIRECTORY;
  }

  @Nullable
  public static String getQuranAudioDirectory(Context context){
    String path = getQuranBaseDirectory(context);
    if (path == null) {
      return null;
    }
    path += AUDIO_DIRECTORY;
    File dir = new File(path);
    if (!dir.exists() && !dir.mkdirs()) {
      return null;
    }

    writeNoMediaFile(path);
    return path + File.separator;
  }

  public static String getQuranImagesBaseDirectory(Context context) {
    String s = QuranFileUtils.getQuranBaseDirectory(context);
    return s == null ? null : s + IMAGES_DIRECTORY;
  }

  private static String getQuranImagesDirectory(Context context) {
    QuranScreenInfo qsi = QuranScreenInfo.getInstance();
    if (qsi == null) {
      return null;
    }
    return getQuranImagesDirectory(context, qsi.getWidthParam());
  }

  private static String getQuranImagesDirectory(Context context, String widthParam) {
    String base = getQuranBaseDirectory(context);
    return (base == null) ? null : base +
        (IMAGES_DIRECTORY.isEmpty() ? "" : IMAGES_DIRECTORY + File.separator) + "width" + widthParam;
  }

  public static String getZipFileUrl() {
    QuranScreenInfo qsi = QuranScreenInfo.getInstance();
    if (qsi == null) {
      return null;
    }
    return getZipFileUrl(qsi.getWidthParam());
  }

  public static String getZipFileUrl(String widthParam) {
    String url = IMG_ZIP_BASE_URL;
    url += "images" + widthParam + ".zip";
    return url;
  }

  public static String getPatchFileUrl(String widthParam, int toVersion) {
    return PATCH_ZIP_BASE_URL + toVersion + "/patch" +
        widthParam + "_v" + toVersion + ".zip";
  }

  private static String getAyaPositionFileName() {
    QuranScreenInfo qsi = QuranScreenInfo.getInstance();
    if (qsi == null) {
      return null;
    }
    return getAyaPositionFileName(qsi.getWidthParam());
  }

  public static String getAyaPositionFileName(String widthParam) {
    return "ayahinfo" + widthParam + ".db";
  }

  public static String getAyaPositionFileUrl() {
    QuranScreenInfo qsi = QuranScreenInfo.getInstance();
    if (qsi == null) {
      return null;
    }
    return getAyaPositionFileUrl(qsi.getWidthParam());
  }

  public static String getAyaPositionFileUrl(String widthParam) {
    return AYAHINFO_BASE_URL + "ayahinfo" + widthParam + ".zip";
  }

  static String getGaplessDatabaseRootUrl() {
    QuranScreenInfo qsi = QuranScreenInfo.getInstance();
    if (qsi == null) {
      return null;
    }
    return AUDIO_DB_BASE_URL;
  }

  public static boolean haveAyaPositionFile(Context context) {
    String base = QuranFileUtils.getQuranAyahDatabaseDirectory(context);
    if (base == null) {
      QuranFileUtils.makeQuranAyahDatabaseDirectory(context);
    }
    String filename = QuranFileUtils.getAyaPositionFileName();
    if (filename != null) {
      String ayaPositionDb = base + File.separator + filename;
      File f = new File(ayaPositionDb);
      return f.exists();
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

  public static boolean hasArabicSearchDatabase(Context context) {
    if (hasTranslation(context, QuranDataProvider.QURAN_ARABIC_DATABASE)) {
      return true;
    } else if (!DATABASE_DIRECTORY.equals(AYAHINFO_DIRECTORY)){
      // non-hafs flavors copy their ayahinfo and arabic search database in a subdirectory,
      // so we copy back the arabic database into the translations directory where it can
      // be shared across all flavors of quran android
      final File ayahInfoFile = new File(getQuranAyahDatabaseDirectory(context),
          QuranDataProvider.QURAN_ARABIC_DATABASE);
      final String baseDir = getQuranDatabaseDirectory(context);
      if (ayahInfoFile.exists() && baseDir != null) {
        final File base = new File(baseDir);
        final File translationsFile = new File(base, QuranDataProvider.QURAN_ARABIC_DATABASE);
        if (base.mkdir()) {
          try {
            copyFile(ayahInfoFile, translationsFile);
            return true;
          } catch (IOException ioe) {
            if (!translationsFile.delete()) {
              Timber.e("Error deleting translations file");
            }
          }
        }
      }
    }
    return false;
  }

  public static String getArabicSearchDatabaseUrl() {
    return DATABASE_BASE_URL + QuranDataProvider.QURAN_ARABIC_DATABASE;
  }

  public static boolean moveAppFiles(Context context, String newLocation) {
    if (QuranSettings.getInstance(context).getAppCustomLocation().equals(newLocation)) {
      return true;
    }
    final String baseDir = getQuranBaseDirectory(context);
    if (baseDir == null) {
      return false;
    }
    File currentDirectory = new File(baseDir);
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
        Timber.e(e, "error moving app files");
      }
    }
    return false;
  }

  private static void deleteFileOrDirectory(File file) {
    if (file.isDirectory()) {
      File[] subFiles = file.listFiles();
      // subFiles is null on some devices, despite this being a directory
      int length = subFiles == null ? 0 : subFiles.length;
      for (int i = 0; i < length; i++) {
        File sf = subFiles[i];
        if (sf.isFile()) {
          if (!sf.delete()) {
            Timber.e("Error deleting %s", sf.getPath());
          }
        } else {
          deleteFileOrDirectory(sf);
        }
      }
    }
    if (!file.delete()) {
      Timber.e("Error deleting %s", file.getPath());
    }
  }

  private static void copyFileOrDirectory(File source, File destination) throws IOException {
    if (source.isDirectory()) {
      if (!destination.exists() && !destination.mkdirs()) {
        return;
      }

      File[] files = source.listFiles();
      for (File f : files) {
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
