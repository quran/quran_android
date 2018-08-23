package com.quran.labs.androidquran.presenter.translation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.common.QuranText;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.data.SuraAyahIterator;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

class BaseTranslationPresenter<T> implements Presenter<T> {
  private final TranslationModel translationModel;
  private final TranslationsDBAdapter translationsAdapter;
  private final Map<String, LocalTranslation> translationMap;
  private final QuranInfo quranInfo;

  @Nullable T translationScreen;
  Disposable disposable;

  BaseTranslationPresenter(TranslationModel translationModel,
                           TranslationsDBAdapter translationsAdapter,
                           QuranInfo quranInfo) {
    this.translationMap = new HashMap<>();
    this.translationModel = translationModel;
    this.translationsAdapter = translationsAdapter;
    this.quranInfo = quranInfo;
  }

  Single<ResultHolder> getVerses(boolean getArabic,
                                 List<String> translations,
                                 VerseRange verseRange) {
    // get all the translations for these verses, using a source of either the list
    // of active translations, or a set of all translations if there are no active
    // translations selected.
    Observable<String> source = !translations.isEmpty()
        ? Observable.fromIterable(translations)
        : getTranslationMapSingle()
            .toObservable()
            .map(t -> {
              List<String> result = new ArrayList<>();
              Set<String> keys = t.keySet();
              for (String key : keys) {
                result.add(t.get(key).filename);
              }
              return result;
            })
            .flatMap(Observable::fromIterable);

    Single<List<List<QuranText>>> translationsObservable =
        source.concatMapEager(db ->
                translationModel.getTranslationFromDatabase(verseRange, db)
                    .map(texts -> ensureProperTranslations(verseRange, texts))
                    .onErrorReturnItem(new ArrayList<>())
                    .toObservable())
            .toList();
    Single<List<QuranText>> arabicObservable = !getArabic ? Single.just(new ArrayList<>()) :
        translationModel.getArabicFromDatabase(verseRange).onErrorReturnItem(new ArrayList<>());
    return Single.zip(arabicObservable, translationsObservable, getTranslationMapSingle(),
        (arabic, texts, map) -> {
          List<QuranAyahInfo> ayahInfo = combineAyahData(verseRange, arabic, texts);
          String[] translationNames = getTranslationNames(translations, map);
          return new ResultHolder(translationNames, ayahInfo);
        })
        .subscribeOn(Schedulers.io());
  }

  List<String> getTranslations(QuranSettings quranSettings) {
    List<String> results = new ArrayList<>();
    results.addAll(quranSettings.getActiveTranslations());
    return results;
  }

  String[] getTranslationNames(@NonNull List<String> translations,
                               @NonNull Map<String, LocalTranslation> translationMap) {
    int translationCount = translations.size();
    String[] result = new String[translationCount];
    for (int i = 0; i < translationCount; i++) {
      String translation = translations.get(i);
      LocalTranslation localTranslation = translationMap.get(translation);
      result[i] = localTranslation == null ? translation : localTranslation.getTranslatorName();
    }

    // fallback to all translations when the map is empty
    if (translationCount == 0) {
      Set<String> keys = translationMap.keySet();
      int mapSize = keys.size();
      result = new String[mapSize];

      int i = 0;
      for (String key : keys) {
        result[i++] = translationMap.get(key).filename;
      }
    }
    return result;
  }

  @NonNull
  List<QuranAyahInfo> combineAyahData(@NonNull VerseRange verseRange,
                                      @NonNull List<QuranText> arabic,
                                      @NonNull List<List<QuranText>> texts) {
    final int arabicSize = arabic.size();
    final int translationCount = texts.size();
    List<QuranAyahInfo> result = new ArrayList<>();
    if (translationCount > 0) {
      final int verses = arabicSize == 0 ? verseRange.versesInRange : arabicSize;
      for (int i = 0; i < verses; i++) {
        QuranText element = arabicSize == 0 ? null : arabic.get(i);
        final List<String> ayahTranslations = new ArrayList<>();
        for (int j = 0; j < translationCount; j++) {
          QuranText item = texts.get(j).size() > i ? texts.get(j).get(i) : null;
          if (item != null) {
            ayahTranslations.add(texts.get(j).get(i).text);
            element = item;
          } else {
            // this keeps the translations aligned with their translators
            // even when a particular translator doesn't load.
            ayahTranslations.add("");
          }
        }

        if (element != null) {
          String arabicText = arabicSize == 0 ? null : arabic.get(i).text;
          result.add(
              new QuranAyahInfo(element.sura, element.ayah, arabicText, ayahTranslations,
                  quranInfo.getAyahId(element.sura, element.ayah)));
        }
      }
    } else if (arabicSize > 0) {
      for (int i = 0; i < arabicSize; i++) {
        QuranText arabicItem = arabic.get(i);
        result.add(new QuranAyahInfo(arabicItem.sura, arabicItem.ayah,
            arabicItem.text, Collections.emptyList(),
            quranInfo.getAyahId(arabicItem.sura, arabicItem.ayah)));
      }
    }
    return result;
  }

  /**
   * Ensures that the list of translations is valid
   * In this case, valid means that the number of verses that we have translations for is either
   * the same as the verse range, or that it's 0 (i.e. due to an error querying the database). If
   * the list has a non-zero length that is less than what the verseRange says, it adds empty
   * entries for those.
   *
   * @param verseRange the range of verses we're trying to get
   * @param texts      the data we got back from the database
   * @return a list of QuranText with a length of either 0 or the verse range
   */
  @NonNull
  List<QuranText> ensureProperTranslations(@NonNull VerseRange verseRange,
                                           @NonNull List<QuranText> texts) {
    int expectedVerses = verseRange.versesInRange;
    int textSize = texts.size();
    if (textSize == 0 || textSize == expectedVerses) {
      return texts;
    }

    // missing some entries for some ayat - this is a work around for bad data in some databases
    // ex. ibn katheer is missing 3 records, 1 in each of suras 5, 17, and 87.
    SuraAyah start = new SuraAyah(verseRange.startSura, verseRange.startAyah);
    SuraAyah end = new SuraAyah(verseRange.endingSura, verseRange.endingAyah);
    SuraAyahIterator iterator = new SuraAyahIterator(quranInfo, start, end);

    int i = 0;
    while (iterator.next()) {
      QuranText item = texts.size() > i ? texts.get(i) : null;
      if (item == null ||
          item.sura != iterator.getSura() ||
          item.ayah != iterator.getAyah()) {
        texts.add(i, new QuranText(iterator.getSura(), iterator.getAyah(), ""));
      }
      i++;
    }
    return texts;
  }

  @NonNull
  private Single<Map<String, LocalTranslation>> getTranslationMapSingle() {
    if (this.translationMap.size() == 0) {
      return Single.fromCallable(translationsAdapter::getTranslations)
          .map(translations -> {
            Map<String, LocalTranslation> map = new HashMap<>();
            for (int i = 0, size = translations.size(); i < size; i++) {
              LocalTranslation translation = translations.get(i);
              map.put(translation.filename, translation);
            }
            return map;
          })
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .doOnSuccess(map -> {
            this.translationMap.clear();
            this.translationMap.putAll(map);
          });
    } else {
      return Single.just(this.translationMap);
    }
  }

  static class ResultHolder {
    final String[] translations;
    final List<QuranAyahInfo> ayahInformation;

    ResultHolder(String[] translations, List<QuranAyahInfo> ayahInformation) {
      this.translations = translations;
      this.ayahInformation = ayahInformation;
    }
  }

  @Override
  public void bind(T what) {
    translationScreen = what;
  }

  @Override
  public void unbind(T what) {
    translationScreen = null;
    if (disposable != null) {
      disposable.dispose();
    }
  }
}
