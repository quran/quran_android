package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.view.AyahToolBar;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;

import java.util.List;
import java.util.Set;

public class AyahTrackerItem {
  final int page;

  AyahTrackerItem(int page) {
    this.page = page;
  }

  void onSetPageBounds(PageCoordinates pageCoordinates) {
  }

  void onSetAyahCoordinates(AyahCoordinates ayahCoordinates) {
  }

  void onSetAyahBookmarks(@NonNull List<Bookmark> bookmarks) {
  }

  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    return false;
  }

  void onHighlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
  }

  void onUnHighlightAyah(int page, int sura, int ayah, HighlightType type) {
  }

  void onUnHighlightAyahType(HighlightType type) {
  }

  @Nullable
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int page, int sura, int ayah,
                                                     int toolBarWidth, int toolBarHeight) {
    return null;
  }

  @Nullable
  SuraAyah getAyahForPosition(int page, float x, float y) {
    return null;
  }

  @Nullable
  QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    return null;
  }

  @Nullable
  LocalTranslation[] getLocalTranslations() {
    return null;
  }
}
