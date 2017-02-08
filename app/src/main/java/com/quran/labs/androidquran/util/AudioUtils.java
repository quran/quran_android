package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.DownloadAudioRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;

public class AudioUtils {

  public static final String AUDIO_EXTENSION = ".mp3";

  private static final String DB_EXTENSION = ".db";
  private static final String ZIP_EXTENSION = ".zip";

  final static class LookAheadAmount {
    static final int PAGE = 1;
    static final int SURA = 2;
    static final int JUZ = 3;

    // make sure to update these when a lookup type is added
    static final int MIN = 1;
    static final int MAX = 3;
  }

  /**
   * Get a list of QariItem representing the qaris to show
   *
   * This method takes into account qaris that exist both in gapped and gapless, and, in those
   * cases, hides the gapped version if it contains no files.
   *
   * @param context the current context
   * @return a list of QariItem representing the qaris to show.
   */
  public static List<QariItem> getQariList(@NonNull Context context) {
    final Resources resources = context.getResources();
    final String[] shuyookh = resources.getStringArray(R.array.quran_readers_name);
    final String[] paths = resources.getStringArray(R.array.quran_readers_path);
    final String[] urls = resources.getStringArray(R.array.quran_readers_urls);
    final String[] databases = resources.getStringArray(R.array.quran_readers_db_name);
    final int[] hasGaplessEquivalent =
        resources.getIntArray(R.array.quran_readers_have_gapless_equivalents);
    List<QariItem> items = new ArrayList<>(shuyookh.length);
    for (int i=0; i < shuyookh.length; i++) {
      if (hasGaplessEquivalent[i] == 0 || haveAnyFiles(context, paths[i])) {
        items.add(new QariItem(i, shuyookh[i], urls[i], paths[i], databases[i]));
      }
    }
    Collections.sort(items, new Comparator<QariItem>() {
      @Override
      public int compare(QariItem lhs, QariItem rhs) {
        boolean lhsGapless = lhs.isGapless();
        boolean rhsGapless = rhs.isGapless();
        if (lhsGapless != rhsGapless) {
          return lhsGapless ? -1 : 1;
        }
        return lhs.getName().compareTo(rhs.getName());
      }
    });
    return items;
  }

  public static String getQariUrl(@NonNull QariItem item) {
    String url = item.getUrl();
    if (item.isGapless()) {
      url += "%03d" + AudioUtils.AUDIO_EXTENSION;
    } else {
      url += "%03d%03d" + AudioUtils.AUDIO_EXTENSION;
    }
    return url;
  }

  public static String getLocalQariUrl(@NonNull Context context, @NonNull QariItem item) {
    String rootDirectory = QuranFileUtils.getQuranAudioDirectory(context);
    return rootDirectory == null ? null : rootDirectory + item.getPath();
  }

  public static String getQariDatabasePathIfGapless(
      @NonNull Context context, @NonNull QariItem item) {
    String databaseName = item.getDatabaseName();
    if (databaseName != null) {
      String path = getLocalQariUrl(context, item);
      if (path != null) {
        databaseName = path + File.separator + databaseName + DB_EXTENSION;
      }
    }
    return databaseName;
  }

  public static boolean shouldDownloadGaplessDatabase(DownloadAudioRequest request) {
    if (!request.isGapless()) {
      return false;
    }
    String dbPath = request.getGaplessDatabaseFilePath();
    if (TextUtils.isEmpty(dbPath)) {
      return false;
    }

    File f = new File(dbPath);
    return !f.exists();
  }

  public static String getGaplessDatabaseUrl(DownloadAudioRequest request) {
    if (!request.isGapless()) {
      return null;
    }

    QariItem item = request.getQariItem();
    String dbname = item.getDatabaseName() + ZIP_EXTENSION;
    return QuranFileUtils.getGaplessDatabaseRootUrl() + "/" + dbname;
  }

  public static SuraAyah getLastAyahToPlay(SuraAyah startAyah,
      int page, int mode, boolean isDualPages) {
    if (isDualPages && mode == LookAheadAmount.PAGE && (page % 2 == 1)) {
      // if we download page by page and we are currently in tablet mode
      // and playing from the right page, get the left page as well.
      page++;
    }

    int pageLastSura = 114;
    int pageLastAyah = 6;
    // page < 0 - intentional, because nextPageAyah looks up the ayah on the next page
    if (page > PAGES_LAST || page < 0) {
      return null;
    }
    if (page < PAGES_LAST) {
      int nextPageSura = QuranInfo.safelyGetSuraOnPage(page);
      // not using [page-1] as an index because we literally want the next page
      int nextPageAyah = QuranInfo.PAGE_AYAH_START[page];

      pageLastSura = nextPageSura;
      pageLastAyah = nextPageAyah - 1;
      if (pageLastAyah < 1) {
        pageLastSura--;
        if (pageLastSura < 1) {
          pageLastSura = 1;
        }
        pageLastAyah = QuranInfo.getNumAyahs(pageLastSura);
      }
    }

    if (mode == LookAheadAmount.SURA) {
      int sura = startAyah.sura;
      int lastAyah = QuranInfo.getNumAyahs(sura);
      if (lastAyah == -1) {
        return null;
      }

      // if we start playback between two suras, download both suras
      if (pageLastSura > sura) {
        sura = pageLastSura;
        lastAyah = QuranInfo.getNumAyahs(sura);
      }
      return new SuraAyah(sura, lastAyah);
    } else if (mode == LookAheadAmount.JUZ) {
      int juz = QuranInfo.getJuzFromPage(page);
      if (juz == 30) {
        return new SuraAyah(114, 6);
      } else if (juz >= 1 && juz < 30) {
        int[] endJuz = QuranInfo.QUARTERS[juz * 8];
        if (pageLastSura > endJuz[0]) {
          // ex between jathiya and a7qaf
          endJuz = QuranInfo.QUARTERS[(juz + 1) * 8];
        } else if (pageLastSura == endJuz[0] &&
            pageLastAyah > endJuz[1]) {
          // ex surat al anfal
          endJuz = QuranInfo.QUARTERS[(juz + 1) * 8];
        }

        return new SuraAyah(endJuz[0], endJuz[1]);
      }
    }

    // page mode (fallback also from errors above)
    return new SuraAyah(pageLastSura, pageLastAyah);
  }

  public static boolean shouldDownloadBasmallah(DownloadAudioRequest request) {
    if (request.isGapless()) {
      return false;
    }
    String baseDirectory = request.getLocalPath();
    if (!TextUtils.isEmpty(baseDirectory)) {
      File f = new File(baseDirectory);
      if (f.exists()) {
        String filename = 1 + File.separator + 1 + AUDIO_EXTENSION;
        f = new File(baseDirectory + File.separator + filename);
        if (f.exists()) {
          Timber.d("already have basmalla...");
          return false;
        }
      } else {
        f.mkdirs();
      }
    }

    return doesRequireBasmallah(request);
  }

  public static boolean haveSuraAyahForQari(String baseDir, int sura, int ayah) {
    String filename = baseDir + File.separator + sura +
        File.separator + ayah + AUDIO_EXTENSION;
    File f = new File(filename);
    return f.exists();
  }

  private static boolean doesRequireBasmallah(AudioRequest request) {
    SuraAyah minAyah = request.getMinAyah();
    int startSura = minAyah.sura;
    int startAyah = minAyah.ayah;

    SuraAyah maxAyah = request.getMaxAyah();
    int endSura = maxAyah.sura;
    int endAyah = maxAyah.ayah;

    Timber.d("seeing if need basmalla...");

    for (int i = startSura; i <= endSura; i++) {
      int lastAyah = QuranInfo.getNumAyahs(i);
      if (i == endSura) {
        lastAyah = endAyah;
      }
      int firstAyah = 1;
      if (i == startSura) {
        firstAyah = startAyah;
      }

      for (int j = firstAyah; j < lastAyah; j++) {
        if (j == 1 && i != 1 && i != 9) {
          Timber.d("need basmalla for %d:%d", i, j);

          return true;
        }
      }
    }

    return false;
  }

  private static boolean haveAnyFiles(Context context, String path) {
    final String basePath = QuranFileUtils.getQuranAudioDirectory(context);
    final File file = new File(basePath, path);
    return file.isDirectory() && file.list().length > 0;
  }

  public static boolean haveAllFiles(DownloadAudioRequest request) {
    String baseDirectory = request.getLocalPath();
    if (TextUtils.isEmpty(baseDirectory)) {
      return false;
    }

    boolean isGapless = request.isGapless();
    File f = new File(baseDirectory);
    if (!f.exists()) {
      f.mkdirs();
      return false;
    }

    SuraAyah minAyah = request.getMinAyah();
    int startSura = minAyah.sura;
    int startAyah = minAyah.ayah;

    SuraAyah maxAyah = request.getMaxAyah();
    int endSura = maxAyah.sura;
    int endAyah = maxAyah.ayah;

    for (int i = startSura; i <= endSura; i++) {
      int lastAyah = QuranInfo.getNumAyahs(i);
      if (i == endSura) {
        lastAyah = endAyah;
      }
      int firstAyah = 1;
      if (i == startSura) {
        firstAyah = startAyah;
      }

      if (isGapless) {
        if (i == endSura && endAyah == 0) {
          continue;
        }
        String p = request.getBaseUrl();
        String fileName = String.format(Locale.US, p, i);
        Timber.d("gapless, checking if we have %s", fileName);
        f = new File(fileName);
        if (!f.exists()) {
          return false;
        }
        continue;
      }

      Timber.d("not gapless, checking each ayah...");
      for (int j = firstAyah; j <= lastAyah; j++) {
        String filename = i + File.separator + j + AUDIO_EXTENSION;
        f = new File(baseDirectory + File.separator + filename);
        if (!f.exists()) {
          return false;
        }
      }
    }

    return true;
  }

  public static Intent getAudioIntent(Context context, String action) {
    final Intent intent = new Intent(context, AudioService.class);
    intent.setAction(action);
    return intent;
  }
}
