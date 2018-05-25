package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.content.Context;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.page.common.data.AyahBounds;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.page.common.data.PageCoordinates;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AyahImageTrackerItem extends AyahTrackerItem<HighlightingImageView> {
  private final QuranInfo quranInfo;
  private final boolean isPageOnRightSide;
  private final int screenHeight;
  @Nullable Map<String, List<AyahBounds>> coordinates;

  public AyahImageTrackerItem(int page,
                              int screenHeight,
                              QuranInfo quranInfo,
                              @NonNull HighlightingImageView highlightingImageView) {
    this(page, screenHeight, quranInfo, false, highlightingImageView);
  }

  public AyahImageTrackerItem(int page,
                              int screenHeight,
                              QuranInfo quranInfo,
                              boolean isPageOnTheRight,
                              @NonNull HighlightingImageView highlightingImageView) {
    super(page, highlightingImageView);
    this.quranInfo = quranInfo;
    this.screenHeight = screenHeight;
    this.isPageOnRightSide = isPageOnTheRight;
  }

  @Override
  void onSetPageBounds(int page, @NonNull RectF bounds) {
    if (this.page == page) {
      // this is only called if overlayText is set
      ayahView.setPageBounds(bounds);
      Context context = ayahView.getContext();
      String suraText = quranInfo.getSuraNameFromPage(context, page, true);
      String juzText = quranInfo.getJuzString(context, page);
      String pageText = QuranUtils.getLocalizedNumber(context, page);
      String rub3Text = QuranDisplayHelper.displayRub3(context, quranInfo, page);
      ayahView.setOverlayText(suraText, juzText, pageText, rub3Text);
    }
  }

  @Override
  void onSetAyahCoordinates(PageCoordinates pageCoordinates) {
    if (this.page == pageCoordinates.getPage()) {
      this.coordinates = pageCoordinates.getAyahCoordinates();
      if (!coordinates.isEmpty()) {
        ayahView.setPageData(pageCoordinates);
        ayahView.invalidate();
      }
    }
  }

  @Override
  void onSetAyahBookmarks(@NonNull List<Bookmark> bookmarks) {
    int highlighted = 0;
    for (int i = 0, size = bookmarks.size(); i < size; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (bookmark.page == page) {
        highlighted++;
        ayahView.highlightAyah(bookmark.sura, bookmark.ayah, HighlightType.BOOKMARK);
      }
    }

    if (highlighted > 0) {
      ayahView.invalidate();
    }
  }

  @Override
  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (this.page == page && coordinates != null) {
      ayahView.highlightAyah(sura, ayah, type);
      ayahView.invalidate();
      return true;
    } else if (coordinates != null) {
      ayahView.unHighlight(type);
    }
    return false;
  }

  @Override
  void onHighlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
    if (this.page == page) {
      ayahView.highlightAyat(ayahKeys, type);
      ayahView.invalidate();
    }
  }

  @Override
  void onUnHighlightAyah(int page, int sura, int ayah, HighlightType type) {
    if (this.page == page) {
      ayahView.unHighlight(sura, ayah, type);
    }
  }

  @Override
  void onUnHighlightAyahType(HighlightType type) {
    ayahView.unHighlight(type);
  }

  @Override
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int page, int sura, int ayah, int toolBarWidth,
                                                     int toolBarHeight) {
    if (this.page == page) {
      final List<AyahBounds> bounds = coordinates == null ? null :
          coordinates.get(sura + ":" + ayah);
      final int screenWidth = ayahView.getWidth();
      if (bounds != null && screenWidth > 0) {
        AyahToolBar.AyahToolBarPosition position =
            ImageAyahUtils.getToolBarPosition(bounds, ayahView.getImageMatrix(),
                screenWidth, screenHeight, toolBarWidth, toolBarHeight);
        if (isPageOnRightSide) {
          // need to adjust offset because our x is really x plus one page
          position.x += ayahView.getWidth();
        }
        return position;
      }
    }
    return null;
  }

  @Nullable
  @Override
  SuraAyah getAyahForPosition(int page, float x, float y) {
    return this.page == page ?
        ImageAyahUtils.getAyahFromCoordinates(coordinates, ayahView, x, y) : null;
  }
}
