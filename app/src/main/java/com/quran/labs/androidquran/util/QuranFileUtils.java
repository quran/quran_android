package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quran.data.source.PageProvider;
import com.quran.labs.androidquran.BuildConfig;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.extension.CloseableExtensionKt;

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

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import timber.log.Timber;

public class QuranFileUtils {
  private static final String QURAN_BASE = "quran_android/";

  // server urls
  private final String IMG_BASE_URL;
  private final String IMG_ZIP_BASE_URL;
  private final String PATCH_ZIP_BASE_URL;
  private final String DATABASE_BASE_URL;
  private final String AYAHINFO_BASE_URL;
  private final String AUDIO_DB_BASE_URL;

  // local paths
  private final String DATABASE_DIRECTORY;
  private final String AUDIO_DIRECTORY;
  private final String AYAHINFO_DIRECTORY;
  private final String IMAGES_DIRECTORY;

  private final QuranScreenInfo quranScreenInfo;
  private final Context appContext;

  @Inject
  public QuranFileUtils(Context context, PageProvider pageProvider, QuranScreenInfo quranScreenInfo) {
    IMG_BASE_URL = pageProvider.getImagesBaseUrl();
    IMG_ZIP_BASE_URL = pageProvider.getImagesZipBaseUrl();
    PATCH_ZIP_BASE_URL = pageProvider.getPatchBaseUrl();
    AYAHINFO_BASE_URL = pageProvider.getAyahInfoBaseUrl();
    DATABASE_DIRECTORY = pageProvider.getDatabaseDirectoryName();
    AUDIO_DIRECTORY = pageProvider.getAudioDirectoryName();
    AYAHINFO_DIRECTORY = pageProvider.getAyahInfoDirectoryName();
    IMAGES_DIRECTORY = pageProvider.getImagesDirectoryName();
    DATABASE_BASE_URL = pageProvider.getDatabasesBaseUrl();
    AUDIO_DB_BASE_URL = pageProvider.getAudioDatabasesBaseUrl();

    this.quranScreenInfo = quranScreenInfo;
    this.appContext = context.getApplicationContext();
  }

  // check if the images with the given width param have a version
  // that we specify (ex if version is 3, check for a .v3 file).
  public boolean isVersion(Context context, String widthParam, int version) {
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

  public String getPotentialFallbackDirectory(Context context, int totalPages) {
    final String state = Environment.getExternalStorageState();
    if (state.equals(Environment.MEDIA_MOUNTED)) {
      if (haveAllImages(context, "_1920", totalPages)) {
        return "1920";
      } else if (haveAllImages(context, "_1280", totalPages)) {
        return "1280";
      } else if (haveAllImages(context, "_1024", totalPages)) {
        return "1024";
      } else {
        return "";
      }
    }
    return null;
  }

  public boolean haveAllImages(Context context, String widthParam, int totalPages) {
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
          for (int i = 1; i <= totalPages; i++) {
            if (!new File(dir, getPageFileName(i)).exists()) {
              Timber.d("haveAllImages: couldn't find page %d", i);
              return false;
            }
          }
        } else if (fileList.length < totalPages) {
          // ideally, we should loop for each page and ensure
          // all pages are there, but this will do for now.
          Timber.d("haveAllImages: found %d files instead of 604.", fileList.length);
          return false;
        }
        return true;
      } else {
        Timber.d("haveAllImages: couldn't find the directory, so making it instead");
        makeQuranDirectory(context);
        if (!IMAGES_DIRECTORY.isEmpty()) {
          makeQuranImagesDirectory(context);
        }
      }
    }
    return false;
  }

  public String getPageFileName(int p) {
    NumberFormat nf = NumberFormat.getInstance(Locale.US);
    nf.setMinimumIntegerDigits(3);
    return "page" + nf.format(p) + ".png";
  }

  private boolean isSDCardMounted() {
    String state = Environment.getExternalStorageState();
    return state.equals(Environment.MEDIA_MOUNTED);
  }

  @NonNull
  public Response getImageFromSD(Context context, String widthParam, String filename) {
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

  private boolean writeNoMediaFile(String parentDir) {
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

  public boolean makeQuranDirectory(Context context) {
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

  private boolean makeQuranImagesDirectory(Context context) {
    return makeDirectory(getQuranImagesDirectory(context));
  }

  private boolean makeDirectory(String path) {
    if (path == null) {
      return false;
    }

    File directory = new File(path);
    return (directory.exists() && directory.isDirectory()) || directory.mkdirs();
  }

  private boolean makeQuranDatabaseDirectory(Context context) {
    return makeDirectory(getQuranDatabaseDirectory(context));
  }

  private boolean makeQuranAyahDatabaseDirectory(Context context) {
    return makeQuranDatabaseDirectory(context) &&
        makeDirectory(getQuranAyahDatabaseDirectory(context));
  }

  public Response getImageFromWeb(OkHttpClient okHttpClient,
      Context context, String filename) {
    return getImageFromWeb(okHttpClient, context, filename, false);
  }

  @NonNull
  private Response getImageFromWeb(OkHttpClient okHttpClient,
      Context context, String filename, boolean isRetry) {
    String urlString = IMG_BASE_URL + "width"
        + quranScreenInfo.getWidthParam() + File.separator
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
          if (path != null && makeQuranDirectory(context)) {
            path += File.separator + filename;
            warning = tryToSaveBitmap(bitmap, path) ? 0 : Response.WARN_COULD_NOT_SAVE_FILE;
          }
          return new Response(bitmap, warning);
        }
      }
    } catch (IOException ioe) {
      Timber.e(ioe, "exception downloading file");
    } finally {
      CloseableExtensionKt.closeQuietly(stream);
    }

    return isRetry ? new Response(Response.ERROR_DOWNLOADING_ERROR) :
        getImageFromWeb(okHttpClient, context, filename, true);
  }

  private Bitmap decodeBitmapStream(InputStream is) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ALPHA_8;
    return BitmapFactory.decodeStream(is, null, options);
  }

  private boolean tryToSaveBitmap(Bitmap bitmap, String savePath) {
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
  public String getQuranBaseDirectory() {
    return getQuranBaseDirectory(appContext);
  }

  @Nullable
  public String getQuranBaseDirectory(Context context) {
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
  public int getAppUsedSpace(Context context) {
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

  public String getQuranDatabaseDirectory(Context context) {
    String base = getQuranBaseDirectory(context);
    return (base == null) ? null : base + DATABASE_DIRECTORY;
  }

  public String getQuranAyahDatabaseDirectory() {
    return getQuranAyahDatabaseDirectory(appContext);
  }

  public String getQuranAyahDatabaseDirectory(Context context) {
    String base = getQuranBaseDirectory(context);
    return base == null ? null : base + File.separator + AYAHINFO_DIRECTORY;
  }

  @Nullable
  public String getQuranAudioDirectory(Context context){
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

  public String getQuranImagesBaseDirectory(Context context) {
    String s = getQuranBaseDirectory(context);
    return s == null ? null : s + IMAGES_DIRECTORY;
  }

  private String getQuranImagesDirectory(Context context) {
    return getQuranImagesDirectory(context, quranScreenInfo.getWidthParam());
  }

  private String getQuranImagesDirectory(Context context, String widthParam) {
    String base = getQuranBaseDirectory(context);
    return (base == null) ? null : base +
        (IMAGES_DIRECTORY.isEmpty() ? "" : IMAGES_DIRECTORY + File.separator) + "width" + widthParam;
  }

  public String getZipFileUrl() {
    return getZipFileUrl(quranScreenInfo.getWidthParam());
  }

  public String getZipFileUrl(String widthParam) {
    String url = IMG_ZIP_BASE_URL;
    url += "images" + widthParam + ".zip";
    return url;
  }

  public String getPatchFileUrl(String widthParam, int toVersion) {
    return PATCH_ZIP_BASE_URL + toVersion + "/patch" +
        widthParam + "_v" + toVersion + ".zip";
  }

  private String getAyaPositionFileName() {
    return getAyaPositionFileName(quranScreenInfo.getWidthParam());
  }

  public String getAyaPositionFileName(String widthParam) {
    return "ayahinfo" + widthParam + ".db";
  }

  public String getAyaPositionFileUrl() {
    return getAyaPositionFileUrl(quranScreenInfo.getWidthParam());
  }

  public String getAyaPositionFileUrl(String widthParam) {
    return AYAHINFO_BASE_URL + "ayahinfo" + widthParam + ".zip";
  }

  String getGaplessDatabaseRootUrl() {
    return AUDIO_DB_BASE_URL;
  }

  public boolean haveAyaPositionFile(Context context) {
    String base = getQuranAyahDatabaseDirectory(context);
    if (base == null) {
      makeQuranAyahDatabaseDirectory(context);
    }
    String filename = getAyaPositionFileName();
    if (filename != null) {
      String ayaPositionDb = base + File.separator + filename;
      File f = new File(ayaPositionDb);
      return f.exists();
    }

    return false;
  }

  public boolean hasTranslation(Context context, String fileName) {
    String path = getQuranDatabaseDirectory(context);
    if (path != null) {
      path += File.separator + fileName;
      return new File(path).exists();
    }
    return false;
  }

  public boolean removeTranslation(Context context, String fileName) {
    String path = getQuranDatabaseDirectory(context);
    if (path != null) {
      path += File.separator + fileName;
      File f = new File(path);
      return f.delete();
    }
    return false;
  }

  public boolean hasArabicSearchDatabase(Context context) {
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

  public String getArabicSearchDatabaseUrl() {
    return DATABASE_BASE_URL + QuranDataProvider.QURAN_ARABIC_DATABASE;
  }

  public boolean moveAppFiles(Context context, String newLocation) {
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

  public void deleteFileOrDirectory(File file) {
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

  private void copyFileOrDirectory(File source, File destination) throws IOException {
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

  private void copyFile(File source, File destination) throws IOException {
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
