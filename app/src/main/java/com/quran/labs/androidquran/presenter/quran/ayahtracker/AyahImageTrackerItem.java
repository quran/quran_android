package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.content.Context;
import android.graphics.RectF;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.view.AyahToolBar;
import com.quran.labs.androidquran.view.HighlightingImageView;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AyahImageTrackerItem extends AyahTrackerItem {
  private final QuranInfo quranInfo;
  private final QuranDisplayData quranDisplayData;
  private final boolean isPageOnRightSide;
  private final int screenHeight;
  private final Set<ImageDrawHelper> imageDrawHelpers;
  @Nullable Map<String, List<AyahBounds>> coordinates;
  @NonNull final HighlightingImageView ayahView;


  public AyahImageTrackerItem(int page,
                              int screenHeight,
                              QuranInfo quranInfo,
                              QuranDisplayData quranDisplayData,
                              @NonNull Set<ImageDrawHelper> imageDrawHelpers,
                              @NonNull HighlightingImageView highlightingImageView) {
    this(page, screenHeight, quranInfo, quranDisplayData, false, imageDrawHelpers, highlightingImageView);
  }

  public AyahImageTrackerItem(int page,
                              int screenHeight,
                              QuranInfo quranInfo,
                              QuranDisplayData quranDisplayData,
                              boolean isPageOnTheRight,
                              @NonNull Set<ImageDrawHelper> imageDrawHelpers,
                              @NonNull HighlightingImageView highlightingImageView) {
    super(page);
    this.ayahView = highlightingImageView;
    this.quranInfo = quranInfo;
    this.quranDisplayData = quranDisplayData;
    this.screenHeight = screenHeight;
    this.imageDrawHelpers = imageDrawHelpers;
    this.isPageOnRightSide = isPageOnTheRight;
  }

  @Override
  void onSetPageBounds(PageCoordinates pageCoordinates) {
    if (this.page == pageCoordinates.getPage()) {
      // this is only called if overlayText is set
      final RectF pageBounds = pageCoordinates.getPageBounds();
      if (!pageBounds.isEmpty()) {
        ayahView.setPageBounds(pageBounds);
        Context context = ayahView.getContext();
        String suraText = quranDisplayData.getSuraNameFromPage(context, page, true);
        String juzText = quranDisplayData.getJuzDisplayStringForPage(context, page);
        String pageText = QuranUtils.getLocalizedNumber(context, page);
        String rub3Text = QuranDisplayHelper.displayRub3(context, quranInfo, page);
        ayahView.setOverlayText(context, suraText, juzText, pageText, rub3Text);
      }
      ayahView.setPageData(pageCoordinates, imageDrawHelpers);
    }
  }

  @Override
  void onSetAyahCoordinates(AyahCoordinates ayahCoordinates) {
    if (this.page == ayahCoordinates.getPage()) {
      this.coordinates = ayahCoordinates.getAyahCoordinates();
      if (!coordinates.isEmpty()) {
        ayahView.setAyahData(ayahCoordinates);
        ayahView.invalidate();
      }
    }
  }

  @Override
  void onSetAyahBookmarks(@NonNull List<Bookmark> bookmarks) {
    int highlighted = 0;
    for (int i = 0, size = bookmarks.size(); i < size; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (bookmark.getPage() == page) {
        highlighted++;
        ayahView.highlightAyah(bookmark.getSura(), bookmark.getAyah(), HighlightType.BOOKMARK);
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
