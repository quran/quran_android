package com.quran.labs.androidquran.presenter.translation;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.common.QuranText;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.presenter.Presenter;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class BaseTranslationPresenterTest {
  private BaseTranslationPresenter<TestPresenter> presenter;

  @Before
  public void setupTest() {
    presenter = new BaseTranslationPresenter<>(null, null);
  }

  @Test
  public void testGetTranslationNames() {
    final List<String> databases = Arrays.asList("one.db", "two.db");
    Map<String, LocalTranslation> map = new HashMap<String, LocalTranslation>() {{
      put("one.db", new LocalTranslation(1, "one.db", "One", "First", null, null, 1));
      put("two.db", new LocalTranslation(2, "two.db", "Two", "Second", null, null, 1));
      put("three.db", new LocalTranslation(2, "three.db", "Three", "Third", null, null, 1));
    }};

    String[] translations = presenter.getTranslationNames(databases, map);
    assertThat(translations).hasLength(2);
    assertThat(translations[0]).isEqualTo("First");
    assertThat(translations[1]).isEqualTo("Second");
  }

  @Test
  public void testHashlessGetTranslationNames() {
    final List<String> databases = Arrays.asList("one.db", "two.db");
    final Map<String, LocalTranslation> map = new HashMap<>();

    String[] translations = presenter.getTranslationNames(databases, map);
    assertThat(translations).hasLength(2);
    assertThat(translations[0]).isEqualTo(databases.get(0));
    assertThat(translations[1]).isEqualTo(databases.get(1));
  }

  @Test
  public void testCombineAyahDataOneVerse() throws Exception {
    VerseRange verseRange = new VerseRange(1, 1, 1, 1);
    List<QuranText> arabic = Collections.singletonList(new QuranText(1, 1, "first ayah"));
    List<QuranAyahInfo> info = presenter.combineAyahData(verseRange, arabic,
        Collections.singletonList(Collections.singletonList(new QuranText(1, 1, "translation"))));

    assertThat(info).hasSize(1);
    QuranAyahInfo first = info.get(0);
    assertThat(first.sura).isEqualTo(1);
    assertThat(first.ayah).isEqualTo(1);
    assertThat(first.texts).hasSize(1);
    assertThat(first.arabicText).isEqualTo("first ayah");
    assertThat(first.texts.get(0)).isEqualTo("translation");
  }

  @Test
  public void testCombineAyahDataOneVerseEmpty() throws Exception {
    VerseRange verseRange = new VerseRange(1, 1, 1, 1);
    List<QuranText> arabic = Collections.emptyList();
    List<QuranAyahInfo> info =
        presenter.combineAyahData(verseRange, arabic, Collections.emptyList());
    assertThat(info).hasSize(0);
  }

  @Test
  public void testCombineAyahDataOneVerseNoArabic() throws Exception {
    VerseRange verseRange = new VerseRange(1, 1, 1, 1);
    List<QuranText> arabic = Collections.emptyList();
    List<QuranAyahInfo> info = presenter.combineAyahData(verseRange, arabic,
        Collections.singletonList(Collections.singletonList(new QuranText(1, 1, "translation"))));

    assertThat(info).hasSize(1);
    QuranAyahInfo first = info.get(0);
    assertThat(first.sura).isEqualTo(1);
    assertThat(first.ayah).isEqualTo(1);
    assertThat(first.texts).hasSize(1);
    assertThat(first.arabicText).isNull();
    assertThat(first.texts.get(0)).isEqualTo("translation");
  }

  @Test
  public void testCombineAyahDataArabicEmptyTranslations() throws Exception {
    VerseRange verseRange = new VerseRange(1, 1, 1, 2);
    List<QuranText> arabic = Arrays.asList(
        new QuranText(1, 1, "first ayah"),
        new QuranText(1, 2, "second ayah")
    );
    List<QuranAyahInfo> info = presenter.combineAyahData(verseRange, arabic, new ArrayList<>());
    assertThat(info).hasSize(2);
    assertThat(info.get(0).sura).isEqualTo(1);
    assertThat(info.get(0).ayah).isEqualTo(1);
    assertThat(info.get(0).texts).hasSize(0);
    assertThat(info.get(0).arabicText).isEqualTo("first ayah");
    assertThat(info.get(1).sura).isEqualTo(1);
    assertThat(info.get(1).ayah).isEqualTo(2);
    assertThat(info.get(1).texts).hasSize(0);
    assertThat(info.get(1).arabicText).isEqualTo("second ayah");
  }

  @Test
  public void testEnsureProperTranslations() {
    VerseRange verseRange = new VerseRange(1, 1, 1, 2);

    List<QuranText> text = new ArrayList<>();
    text.add(new QuranText(1, 1, "bismillah"));

    text = presenter.ensureProperTranslations(verseRange, text);
    assertThat(text).hasSize(2);

    QuranText first = text.get(0);
    assertThat(first.sura).isEqualTo(1);
    assertThat(first.ayah).isEqualTo(1);
    assertThat(first.text).isEqualTo("bismillah");

    QuranText second = text.get(1);
    assertThat(second.sura).isEqualTo(1);
    assertThat(second.ayah).isEqualTo(2);
    assertThat(second.text).isEmpty();
  }

  private static class TestPresenter implements Presenter {

    @Override
    public void bind(Object what) {
    }

    @Override
    public void unbind(Object what) {
    }
  }

}
