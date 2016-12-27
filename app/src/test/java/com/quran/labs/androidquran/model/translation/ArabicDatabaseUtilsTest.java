package com.quran.labs.androidquran.model.translation;

import android.content.Context;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkWithAyahText;
import com.quran.labs.androidquran.database.DatabaseHandler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class ArabicDatabaseUtilsTest {
  @Mock Context context;
  @Mock DatabaseHandler arabicHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(ArabicDatabaseUtilsTest.this);
  }

  @Test
  public void testHydrateAyahText() {
    ArabicDatabaseUtils arabicDatabaseUtils = new ArabicDatabaseUtils(context) {

      @Override
      DatabaseHandler getArabicDatabaseHandler() {
        return arabicHandler;
      }

      @Override
      Map<Integer, String> getAyahTextForAyat(List<Integer> ayat) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer ayahId : ayat) {
          result.put(ayahId, "verse " + ayahId);
        }
        return result;
      }
    };

    List<Bookmark> bookmarks = new ArrayList<>(3);
    bookmarks.add(new Bookmark(1, 1, 1, 1));
    bookmarks.add(new Bookmark(2, null, null, 3));
    bookmarks.add(new Bookmark(3, 114, 6, 604));

    List<Bookmark> result = arabicDatabaseUtils.hydrateAyahText(bookmarks);
    assertThat(result).hasSize(3);
    assertThat(result.get(0)).isInstanceOf(BookmarkWithAyahText.class);
    assertThat(result.get(1)).isNotInstanceOf(BookmarkWithAyahText.class);
    assertThat(result.get(2)).isInstanceOf(BookmarkWithAyahText.class);

    assertThat(result.get(0).getAyahText()).isNotEmpty();
    assertThat(result.get(1).getAyahText()).isNull();
    assertThat(result.get(0).getAyahText()).isNotEmpty();

    assertThat(result).isNotSameAs(bookmarks);
  }

  @Test
  public void testHydrateAyahTextEmpty() {
    ArabicDatabaseUtils arabicDatabaseUtils = new ArabicDatabaseUtils(context) {
      @Override
      DatabaseHandler getArabicDatabaseHandler() {
        return arabicHandler;
      }

      @Override
      Map<Integer, String> getAyahTextForAyat(List<Integer> ayat) {
        Map<Integer, String> result = new HashMap<>();
        for (Integer ayahId : ayat) {
          result.put(ayahId, "verse " + ayahId);
        }
        return result;
      }
    };

    List<Bookmark> bookmarks = new ArrayList<>(1);
    bookmarks.add(new Bookmark(1, null, null, 3));

    List<Bookmark> result = arabicDatabaseUtils.hydrateAyahText(bookmarks);
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isNotInstanceOf(BookmarkWithAyahText.class);
    assertThat(result.get(0).getAyahText()).isNull();
    assertThat(result).isSameAs(bookmarks);
  }

  @Test
  public void testGetFirstFewWordsFromAyah() {
    int total = ArabicDatabaseUtils.NUMBER_OF_WORDS;
    for (int i = 1; i < total; i++) {
      String text = makeText(i);
      assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, text)).isSameAs(text);
    }

    String veryLongString = makeText(100);
    assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, veryLongString))
        .isEqualTo(makeText(4));
  }

  private String makeText(int words) {
    String result = "";
    for (int i=0; i < words; i++) {
      if (i > 0) {
        result += " ";
      }
      result += "word" + i;
    }
    return result;
  }

  @Test
  public void testGetAyahWithoutBasmallah() {
    String basmallah = ArabicDatabaseUtils.AR_BASMALLAH;

    String original = basmallah + " first ayah";
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(1, 1, original)).isSameAs(original);
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(9, 1, original)).isSameAs(original);
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 4, original)).isSameAs(original);

    assertThat(ArabicDatabaseUtils
        .getAyahWithoutBasmallah(4, 1, original)).isEqualTo("first ayah");
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 1, "first ayah"))
        .isEqualTo("first ayah");
  }
}
