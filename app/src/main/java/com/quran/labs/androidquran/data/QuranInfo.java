package com.quran.labs.androidquran.data;

import android.content.Context;
import android.text.TextUtils;

import com.crashlytics.android.Crashlytics;
import com.quran.data.model.SuraAyah;
import com.quran.data.model.VerseRange;
import com.quran.data.source.PageProvider;
import com.quran.data.source.QuranDataSource;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

public class QuranInfo {
  private final int[] suraPageStart;
  private final int[] suraNumAyahs;
  private final boolean[] suraIsMakki;
  private final int[][] quarters;
  private final int numberOfPages;
  private final int numberOfPagesDual;
  private final com.quran.data.core.QuranInfo quranInfo;

  @Inject
  public QuranInfo(PageProvider pageProvider) {
    final QuranDataSource quranDataSource = pageProvider.getDataSource();
    quranInfo = new com.quran.data.core.QuranInfo(quranDataSource);

    suraPageStart = quranDataSource.getPageForSuraArray();
    suraNumAyahs = quranDataSource.getNumberOfAyahsForSuraArray();
    suraIsMakki = quranDataSource.getIsMakkiBySuraArray();
    quarters = quranDataSource.getQuartersArray();
    numberOfPages = quranDataSource.getNumberOfPages();
    numberOfPagesDual = numberOfPages / 2;
  }

  public int getNumberOfPages() {
    return numberOfPages;
  }

  public int getNumberOfPagesDual() {
    return numberOfPagesDual;
  }

  /**
   * Get localized sura name from resources
   *
   * @param context    Application context
   * @param sura       Sura number (1~114)
   * @param wantPrefix Whether or not to show prefix "Sura"
   * @return Compiled sura name without translations
   */
  public String getSuraName(Context context, int sura, boolean wantPrefix) {
    return getSuraName(context, sura, wantPrefix, false);
  }

  /**
   * Get localized sura name from resources
   *
   * @param context         Application context
   * @param sura            Sura number (1~114)
   * @param wantPrefix      Whether or not to show prefix "Sura"
   * @param wantTranslation Whether or not to show sura name translations
   * @return Compiled sura name based on provided arguments
   */
  public String getSuraName(Context context, int sura, boolean wantPrefix, boolean wantTranslation) {
    if (sura < Constants.SURA_FIRST ||
        sura > Constants.SURA_LAST) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    String[] suraNames = context.getResources().getStringArray(R.array.sura_names);
    if (wantPrefix) {
      builder.append(context.getString(R.string.quran_sura_title, suraNames[sura - 1]));
    } else {
      builder.append(suraNames[sura - 1]);
    }
    if (wantTranslation) {
      String translation = context.getResources().getStringArray(R.array.sura_names_translation)[sura - 1];
      if (!TextUtils.isEmpty(translation)) {
        // Some sura names may not have translation
        builder.append(" (");
        builder.append(translation);
        builder.append(")");
      }
    }

    return builder.toString();
  }

  public int getStartingPageForJuz(int juz) {
    return quranInfo.getStartingPageForJuz(juz);
  }

  public int getPageNumberForSura(int sura) {
    return quranInfo.getPageNumberForSura(sura);
  }

  public int getSuraNumberFromPage(int page) {
    return quranInfo.getSuraNumberFromPage(page);
  }

  public List<Integer> getListOfSurahWithStartingOnPage(int page) {
    return quranInfo.getListOfSurahWithStartingOnPage(page);
  }

  public String getSuraNameFromPage(Context context, int page,
                                           boolean wantTitle) {
    int sura = getSuraNumberFromPage(page);
    return (sura > 0) ? getSuraName(context, sura, wantTitle, false) : "";
  }

  public String getPageSubtitle(Context context, int page) {
    String description = context.getString(R.string.page_description);
    return String.format(description,
        QuranUtils.getLocalizedNumber(context, page),
        QuranUtils.getLocalizedNumber(context, getJuzForDisplayFromPage(page)));
  }

  public String getJuzDisplayStringForPage(Context context, int page) {
    String description = context.getString(R.string.juz2_description);
    return String.format(description, QuranUtils.getLocalizedNumber(context,
        getJuzForDisplayFromPage(page)));
  }

  public String getSuraAyahString(Context context, int sura, int ayah) {
    String suraName = getSuraName(context, sura, false, false);
    return context.getString(R.string.sura_ayah_notification_str, suraName, ayah);
  }

  public String getNotificationTitle(Context context,
                                            SuraAyah minVerse,
                                            SuraAyah maxVerse,
                                            boolean isGapless) {
    int minSura = minVerse.sura;
    int maxSura = maxVerse.sura;

    String notificationTitle =
        getSuraName(context, minSura, true, false);
    if (isGapless) {
      // for gapless, don't show the ayah numbers since we're
      // downloading the entire sura(s).
      if (minSura == maxSura) {
        return notificationTitle;
      } else {
        return notificationTitle + " - " +
            getSuraName(context, maxSura, true, false);
      }
    }

    int maxAyah = maxVerse.ayah;
    if (maxAyah == 0) {
      maxSura--;
      maxAyah = getNumAyahs(maxSura);
    }

    if (minSura == maxSura) {
      if (minVerse.ayah == maxAyah) {
        notificationTitle += " (" + maxAyah + ")";
      } else {
        notificationTitle += " (" + minVerse.ayah +
            "-" + maxAyah + ")";
      }
    } else {
      notificationTitle += " (" + minVerse.ayah +
          ") - " + getSuraName(context, maxSura, true, false) +
          " (" + maxAyah + ")";
    }

    return notificationTitle;
  }

  public String getSuraListMetaString(Context context, int sura) {
    String info = context.getString(suraIsMakki[sura - 1]
        ? R.string.makki : R.string.madani) + " - ";

    int ayahs = suraNumAyahs[sura - 1];
    info += context.getResources().getQuantityString(R.plurals.verses, ayahs,
        QuranUtils.getLocalizedNumber(context, ayahs));
    return info;
  }

  public VerseRange getVerseRangeForPage(int page) {
    return quranInfo.getVerseRangeForPage(page);
  }

  public int getFirstAyahOnPage(int page) {
    return quranInfo.getFirstAyahOnPage(page);
  }

  @NonNull
  public int[] getPageBounds(int page) {
    return quranInfo.getPageBounds(page);
  }

  public int safelyGetSuraOnPage(int page) {
    if (page < Constants.PAGES_FIRST || page > numberOfPages) {
      Crashlytics.logException(new IllegalArgumentException("got page: " + page));
      page = 1;
    }
    return getSuraOnPage(page);
  }

  public int getSuraOnPage(int page) {
    return quranInfo.getSuraOnPage(page);
  }

  private String getSuraNameFromPage(Context context, int page) {
    for (int i = 0; i < Constants.SURAS_COUNT; i++) {
      if (suraPageStart[i] == page) {
        return getSuraName(context, i + 1, false, false);
      } else if (suraPageStart[i] > page) {
        return getSuraName(context, i, false, false);
      }
    }
    return "";
  }

  /**
   * Gets the juz' that should be printed at the top of the page
   * This may be different than the actual juz' for the page (for example, juz' 7 starts at page
   * 121, but despite this, the title of the page is juz' 6).
   *
   * @param page the page
   * @return the display juz' display string for the page
   */
  @VisibleForTesting
  int getJuzForDisplayFromPage(int page) {
    return quranInfo.getJuzForDisplayFromPage(page);
  }

  public int getJuzFromPage(int page) {
    return quranInfo.getJuzFromPage(page);
  }

  private int getJuzFromSuraAyah(int sura, int ayah, int juz) {
    if (juz == 30) {
      return juz;
    }

    // get the starting point of the next juz'
    final int[] lastQuarter = quarters[juz * 8];
    // if we're after that starting point, return juz + 1
    if (sura > lastQuarter[0] || (lastQuarter[0] == sura && ayah >= lastQuarter[1])) {
      return juz + 1;
    } else {
       // otherwise just return this juz
      return juz;
    }
  }

  public int getRub3FromPage(int page) {
    return quranInfo.getRub3FromPage(page);
  }

  public int getPageFromSuraAyah(int sura, int ayah) {
    return quranInfo.getPageFromSuraAyah(sura, ayah);
  }

  public int getAyahId(int sura, int ayah) {
    return quranInfo.getAyahId(sura, ayah);
  }

  public SuraAyah getSuraAyahFromAyahId(int ayahId) {
    int sura = 0;
    int ayahIdentifier = ayahId;
    while (ayahIdentifier > suraNumAyahs[sura]) {
      ayahIdentifier -= suraNumAyahs[sura++];
    }
    return new SuraAyah(sura + 1, ayahIdentifier);
  }

  public int getNumAyahs(int sura) {
    return quranInfo.getNumberOfAyahs(sura);
  }

  public  int getPageFromPos(int position, boolean dual) {
    return quranInfo.getPageFromPosition(position, dual);
  }

  public int getPosFromPage(int page, boolean dual) {
    return quranInfo.getPositionFromPage(page, dual);
  }

  public String getAyahString(int sura, int ayah, Context context) {
    return getSuraName(context, sura, true) + " - " + context.getString(R.string.quran_ayah, ayah);
  }

  public  String getAyahMetadata(int sura, int ayah, int page, Context context) {
    int juz = getJuzForDisplayFromPage(page);
    return context.getString(R.string.quran_ayah_details, getSuraName(context, sura, true),
        QuranUtils.getLocalizedNumber(context, ayah),
        QuranUtils.getLocalizedNumber(context, getJuzFromSuraAyah(sura, ayah, juz)));
  }

  public  String getSuraNameString(Context context, int page) {
    return context.getString(R.string.quran_sura_title, getSuraNameFromPage(context, page));
  }

  // not ideal, should change this later
  public  int[] getQuarterByIndex(int quarter) {
    return quarters[quarter];
  }

  public Set<String> getAyahKeysOnPage(int page, SuraAyah lowerBound, SuraAyah upperBound) {
    Set<String> ayahKeys = new LinkedHashSet<>();
    int[] bounds = getPageBounds(page);
    SuraAyah start = new SuraAyah(bounds[0], bounds[1]);
    SuraAyah end = new SuraAyah(bounds[2], bounds[3]);
    if (lowerBound != null) {
      start = SuraAyah.max(start, lowerBound);
    }
    if (upperBound != null) {
      end = SuraAyah.min(end, upperBound);
    }
    SuraAyahIterator iterator = new SuraAyahIterator(this, start, end);
    while (iterator.next()) {
      ayahKeys.add(iterator.getSura() + ":" + iterator.getAyah());
    }
    return ayahKeys;
  }

}
