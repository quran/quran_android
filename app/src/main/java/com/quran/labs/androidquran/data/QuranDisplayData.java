package com.quran.labs.androidquran.data;

import android.content.Context;
import android.text.TextUtils;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.data.source.PageProvider;
import com.quran.data.source.QuranDataSource;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;
import timber.log.Timber;

public class QuranDisplayData {
  private final QuranInfo quranInfo;

  @Inject
  public QuranDisplayData(QuranInfo quranInfo) {
    this.quranInfo = quranInfo;
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

  public String getSuraNameFromPage(Context context, int page,
                                           boolean wantTitle) {
    int sura = quranInfo.getSuraNumberFromPage(page);
    return (sura > 0) ? getSuraName(context, sura, wantTitle, false) : "";
  }

  public String getPageSubtitle(Context context, int page) {
    String description = context.getString(R.string.page_description);
    return String.format(description,
        QuranUtils.getLocalizedNumber(context, page),
        QuranUtils.getLocalizedNumber(context, quranInfo.getJuzForDisplayFromPage(page)));
  }

  public String getJuzDisplayStringForPage(Context context, int page) {
    String description = context.getString(R.string.juz2_description);
    return String.format(description, QuranUtils.getLocalizedNumber(context,
        quranInfo.getJuzForDisplayFromPage(page)));
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
      maxAyah = quranInfo.getNumberOfAyahs(maxSura);
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
    String info = context.getString(quranInfo.isMakki(sura)
        ? R.string.makki : R.string.madani) + " - ";

    int ayahs = quranInfo.getNumberOfAyahs(sura);
    info += context.getResources().getQuantityString(R.plurals.verses, ayahs,
        QuranUtils.getLocalizedNumber(context, ayahs));
    return info;
  }

  public int safelyGetSuraOnPage(int page) {
    if (page < Constants.PAGES_FIRST || page > quranInfo.getNumberOfPages()) {
      Timber.e(new IllegalArgumentException("safelyGetSuraOnPage with page: " + page));
      page = 1;
    }
    return quranInfo.getSuraOnPage(page);
  }

  private String getSuraNameFromPage(Context context, int page) {
    final int suraNumber = quranInfo.getSuraNumberFromPage(page);
    return getSuraName(context, suraNumber, false, false);
  }

  public String getAyahString(int sura, int ayah, Context context) {
    return getSuraName(context, sura, true) + " - " + context.getString(R.string.quran_ayah, ayah);
  }

  public  String getAyahMetadata(int sura, int ayah, int page, Context context) {
    int juz = quranInfo.getJuzForDisplayFromPage(page);
    return context.getString(R.string.quran_ayah_details, getSuraName(context, sura, true),
        QuranUtils.getLocalizedNumber(context, ayah),
        QuranUtils.getLocalizedNumber(context, quranInfo.getJuzFromSuraAyah(sura, ayah, juz)));
  }

  public  String getSuraNameString(Context context, int page) {
    return context.getString(R.string.quran_sura_title, getSuraNameFromPage(context, page));
  }

  public Set<String> getAyahKeysOnPage(int page, SuraAyah lowerBound, SuraAyah upperBound) {
    Set<String> ayahKeys = new LinkedHashSet<>();
    int[] bounds = quranInfo.getPageBounds(page);
    SuraAyah start = new SuraAyah(bounds[0], bounds[1]);
    SuraAyah end = new SuraAyah(bounds[2], bounds[3]);
    if (lowerBound != null) {
      start = SuraAyah.max(start, lowerBound);
    }
    if (upperBound != null) {
      end = SuraAyah.min(end, upperBound);
    }
    SuraAyahIterator iterator = new SuraAyahIterator(this.quranInfo, start, end);
    while (iterator.next()) {
      ayahKeys.add(iterator.getSura() + ":" + iterator.getAyah());
    }
    return ayahKeys;
  }

}
