package com.quran.labs.androidquran.data;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.QuranUtils;

import android.content.Context;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;
import static com.quran.labs.androidquran.data.Constants.PAGES_LAST_DUAL;

public class BaseQuranInfo {
  public static int[] SURA_PAGE_START = QuranData.SURA_PAGE_START;
  public static int[] PAGE_SURA_START = QuranData.PAGE_SURA_START;
  public static int[] PAGE_AYAH_START = QuranData.PAGE_AYAH_START;
  public static int[] JUZ_PAGE_START = QuranData.JUZ_PAGE_START;
  public static int[] PAGE_RUB3_START = QuranData.PAGE_RUB3_START;
  public static int[] SURA_NUM_AYAHS = QuranData.SURA_NUM_AYAHS;
  public static boolean[] SURA_IS_MAKKI = QuranData.SURA_IS_MAKKI;
  public static int[][] QUARTERS = QuranData.QUARTERS;

  /**
   * Get localized sura name from resources
   *
   * @param context    Application context
   * @param sura       Sura number (1~114)
   * @param wantPrefix Whether or not to show prefix "Sura"
   * @return Compiled sura name without translations
   */
  public static String getSuraName(Context context, int sura, boolean wantPrefix) {
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
  public static String getSuraName(Context context, int sura,
                                   boolean wantPrefix, boolean wantTranslation) {
    if (sura < Constants.SURA_FIRST ||
        sura > Constants.SURA_LAST) {
      return "";
    }

    StringBuilder builder = new StringBuilder();
    if (wantPrefix) {
      builder.append(context.getString(R.string.quran_sura_title,
          context.getResources().getStringArray(R.array.sura_names)[sura - 1]));
    } else {
      builder.append(context.getResources().getStringArray(R.array.sura_names)[sura - 1]);
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

  public static int getSuraNumberFromPage(int page) {
    int sura = -1;
    for (int i = 0; i < Constants.SURAS_COUNT; i++) {
      if (SURA_PAGE_START[i] == page) {
        sura = i + 1;
        break;
      } else if (SURA_PAGE_START[i] > page) {
        sura = i;
        break;
      }
    }

    return sura;
  }

  public static String getSuraNameFromPage(Context context, int page,
                                           boolean wantTitle) {
    int sura = getSuraNumberFromPage(page);
    return (sura > 0) ? getSuraName(context, sura, wantTitle, false) : "";
  }

  public static String getPageSubtitle(Context context, int page) {
    String description = context.getString(R.string.page_description);
    return String.format(description,
        QuranUtils.getLocalizedNumber(context, page),
        QuranUtils.getLocalizedNumber(context,
            QuranInfo.getJuzFromPage(page)));
  }

  public static String getJuzString(Context context, int page) {
    String description = context.getString(R.string.juz2_description);
    return String.format(description, QuranUtils.getLocalizedNumber(
        context, QuranInfo.getJuzFromPage(page)));
  }

  public static String getSuraAyahString(Context context, int sura, int ayah) {
    String suraName = getSuraName(context, sura, false, false);
    return context.getString(R.string.sura_ayah_notification_str, suraName, ayah);
  }

  public static String getNotificationTitle(Context context,
                                            QuranAyah minVerse,
                                            QuranAyah maxVerse,
                                            boolean isGapless) {
    int minSura = minVerse.getSura();
    int maxSura = maxVerse.getSura();

    String notificationTitle =
        QuranInfo.getSuraName(context, minSura, true, false);
    if (isGapless) {
      // for gapless, don't show the ayah numbers since we're
      // downloading the entire sura(s).
      if (minSura == maxSura) {
        return notificationTitle;
      } else {
        return notificationTitle + " - " +
            QuranInfo.getSuraName(context, maxSura, true, false);
      }
    }

    int maxAyah = maxVerse.getAyah();
    if (maxAyah == 0) {
      maxSura--;
      maxAyah = QuranInfo.getNumAyahs(maxSura);
    }

    if (minSura == maxSura) {
      if (minVerse.getAyah() == maxAyah) {
        notificationTitle += " (" + maxAyah + ")";
      } else {
        notificationTitle += " (" + minVerse.getAyah() +
            "-" + maxAyah + ")";
      }
    } else {
      notificationTitle += " (" + minVerse.getAyah() +
          ") - " + QuranInfo.getSuraName(context, maxSura, true, false) +
          " (" + maxAyah + ")";
    }

    return notificationTitle;
  }

  public static String getSuraListMetaString(Context context, int sura) {
    String info = context.getString(QuranInfo.SURA_IS_MAKKI[sura - 1]
        ? R.string.makki : R.string.madani) + " - ";

    int ayahs = QuranInfo.SURA_NUM_AYAHS[sura - 1];
    info += context.getResources().getQuantityString(R.plurals.verses, ayahs,
        QuranUtils.getLocalizedNumber(context, ayahs));
    return info;
  }

  public static Integer[] getPageBounds(int page) {
    if (page > PAGES_LAST)
      page = PAGES_LAST;
    if (page < 1) page = 1;

    Integer[] bounds = new Integer[4];
    bounds[0] = PAGE_SURA_START[page - 1];
    bounds[1] = PAGE_AYAH_START[page - 1];
    if (page == PAGES_LAST) {
      bounds[2] = Constants.SURA_LAST;
      bounds[3] = 6;
    } else {
      int nextPageSura = PAGE_SURA_START[page];
      int nextPageAyah = PAGE_AYAH_START[page];

      if (nextPageSura == bounds[0]) {
        bounds[2] = bounds[0];
        bounds[3] = nextPageAyah - 1;
      } else {
        if (nextPageAyah > 1) {
          bounds[2] = nextPageSura;
          bounds[3] = nextPageAyah - 1;
        } else {
          bounds[2] = nextPageSura - 1;
          bounds[3] = SURA_NUM_AYAHS[bounds[2] - 1];
        }
      }
    }
    return bounds;
  }

  public static String getSuraNameFromPage(Context context, int page) {
    for (int i = 0; i < Constants.SURAS_COUNT; i++) {
      if (SURA_PAGE_START[i] == page) {
        return getSuraName(context, i + 1, false, false);
      } else if (SURA_PAGE_START[i] > page) {
        return getSuraName(context, i, false, false);
      }
    }
    return "";
  }

  public static int getJuzFromPage(int page) {
    int juz = ((page - 2) / 20) + 1;
    return juz > 30 ? 30 : juz < 1 ? 1 : juz;
  }

  public static int getRub3FromPage(int page) {
    if ((page > PAGES_LAST) || (page < 1)) return -1;
    return PAGE_RUB3_START[page - 1];
  }

  public static int getPageFromSuraAyah(int sura, int ayah) {
    // basic bounds checking
    if (ayah == 0) ayah = 1;
    if ((sura < 1) || (sura > Constants.SURAS_COUNT)
        || (ayah < Constants.AYA_MIN) ||
        (ayah > Constants.AYA_MAX))
      return -1;

    // what page does the sura start on?
    int index = QuranInfo.SURA_PAGE_START[sura - 1] - 1;
    while (index < PAGES_LAST) {
      // what's the first sura in that page?
      int ss = QuranInfo.PAGE_SURA_START[index];

      // if we've passed the sura, return the previous page
      // or, if we're at the same sura and passed the ayah
      if (ss > sura || ((ss == sura) &&
          (QuranInfo.PAGE_AYAH_START[index] > ayah))) {
        break;
      }

      // otherwise, look at the next page
      index++;
    }

    return index;
  }

  public static int getAyahId(int sura, int ayah) {
    int ayahId = 0;
    for (int i = 0; i < sura - 1; i++) {
      ayahId += SURA_NUM_AYAHS[i];
    }
    ayahId += ayah;
    return ayahId;
  }

  public static int getNumAyahs(int sura) {
    if ((sura < 1) || (sura > Constants.SURAS_COUNT)) return -1;
    return SURA_NUM_AYAHS[sura - 1];
  }

  public static int getPageFromPos(int position, boolean dual) {
    int page = PAGES_LAST - position;
    if (dual) {
      page = (PAGES_LAST_DUAL - position) * 2;
    }
    return page;
  }

  public static int getPosFromPage(int page, boolean dual) {
    int position = PAGES_LAST - page;
    if (dual) {
      if (page % 2 != 0) {
        page++;
      }
      position = PAGES_LAST_DUAL - (page / 2);
    }
    return position;
  }

  public static String getAyahString(int sura, int ayah, Context context) {
    return getSuraName(context, sura, true) + " - " + context.getString(R.string.quran_ayah,
        Integer.valueOf(QuranUtils.getLocalizedNumber(context, ayah)));
  }

  public static String getAyahMetadata(int sura, int ayah, int page, Context context) {
    int juz = getJuzFromPage(page);
    return context.getString(R.string.quran_ayah_details, getSuraName(context, sura, true),
        QuranUtils.getLocalizedNumber(context, ayah), QuranUtils.getLocalizedNumber(context, juz));
  }

  public static String getSuraNameString(Context context, int page) {
    return context.getString(R.string.quran_sura_title, getSuraNameFromPage(context, page));
  }

  public static Set<String> getAyahKeysOnPage(int page, SuraAyah lowerBound, SuraAyah upperBound) {
    Set<String> ayahKeys = new LinkedHashSet<>();
    Integer[] bounds = QuranInfo.getPageBounds(page);
    if (bounds != null) {
      SuraAyah start = new SuraAyah(bounds[0], bounds[1]);
      SuraAyah end = new SuraAyah(bounds[2], bounds[3]);
      if (lowerBound != null) {
        start = SuraAyah.max(start, lowerBound);
      }
      if (upperBound != null) {
        end = SuraAyah.min(end, upperBound);
      }
      SuraAyah.Iterator iterator = SuraAyah.getIterator(start, end);
      while (iterator.next()) {
        ayahKeys.add(iterator.getSura() + ":" + iterator.getAyah());
      }
    }
    return ayahKeys;
  }

}
