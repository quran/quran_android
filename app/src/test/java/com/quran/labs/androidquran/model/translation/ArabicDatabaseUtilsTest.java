package com.quran.labs.androidquran.model.translation;

import android.content.Context;

import com.quran.data.core.QuranInfo;
import com.quran.data.pageinfo.common.MadaniDataSource;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.util.QuranFileUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class ArabicDatabaseUtilsTest {
  @Mock Context context;
  @Mock DatabaseHandler arabicHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(ArabicDatabaseUtilsTest.this);
  }

  @Test
  public void testHydrateAyahText() {
    ArabicDatabaseUtils arabicDatabaseUtils = getArabicDatabaseUtils();

    List<Bookmark> bookmarks = new ArrayList<>(3);
    bookmarks.add(new Bookmark(1, 1, 1, 1));
    bookmarks.add(new Bookmark(2, null, null, 3));
    bookmarks.add(new Bookmark(3, 114, 6, 604));

    List<Bookmark> result = arabicDatabaseUtils.hydrateAyahText(bookmarks);
    assertThat(result).hasSize(3);

    assertThat(result.get(0).getAyahText()).isNotEmpty();
    assertThat(result.get(1).getAyahText()).isNull();
    assertThat(result.get(0).getAyahText()).isNotEmpty();

    assertThat(result).isNotSameInstanceAs(bookmarks);
  }

  @Test
  public void testHydrateAyahTextEmpty() {
    getArabicDatabaseUtils();
    ArabicDatabaseUtils arabicDatabaseUtils = getArabicDatabaseUtils();

    List<Bookmark> bookmarks = new ArrayList<>(1);
    bookmarks.add(new Bookmark(1, null, null, 3));

    List<Bookmark> result = arabicDatabaseUtils.hydrateAyahText(bookmarks);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getAyahText()).isNull();
    assertThat(result).isSameInstanceAs(bookmarks);
  }

  private ArabicDatabaseUtils getArabicDatabaseUtils() {
    return new ArabicDatabaseUtils(context,
        new QuranInfo(new MadaniDataSource()),
        mock(QuranFileUtils.class)) {
      @Override
      DatabaseHandler getArabicDatabaseHandler() {
        return arabicHandler;
      }

      @Override
      public Map<Integer, String> getAyahTextForAyat(List<Integer> ayat) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer ayahId : ayat) {
          result.put(ayahId, "verse " + ayahId);
        }
        return result;
      }
    };
  }

  @Test
  public void testGetFirstFewWordsFromAyah() {
    int total = ArabicDatabaseUtils.NUMBER_OF_WORDS;
    for (int i = 1; i < total; i++) {
      String text = makeText(i);
      assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, text)).isSameInstanceAs(text);
    }

    String veryLongString = makeText(100);
    assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, veryLongString))
        .isEqualTo(makeText(4));
  }

  private String makeText(int words) {
    StringBuilder result = new StringBuilder();
    for (int i=0; i < words; i++) {
      if (i > 0) {
        result.append(" ");
      }
      result.append("word").append(i);
    }
    return result.toString();
  }

  @Test
  public void testGetAyahWithoutBasmallah() {
    String basmallah = ArabicDatabaseUtils.AR_BASMALLAH_IN_TEXT;

    String original = basmallah + " first ayah";
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(1, 1, original)).isSameInstanceAs(original);
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(9, 1, original)).isSameInstanceAs(original);
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 4, original)).isSameInstanceAs(original);

    assertThat(ArabicDatabaseUtils
        .getAyahWithoutBasmallah(4, 1, original)).isEqualTo("first ayah");
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 1, "first ayah"))
        .isEqualTo("first ayah");
  }
}
