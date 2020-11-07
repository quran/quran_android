package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.graphics.Matrix;
import android.graphics.RectF;

import com.quran.data.core.QuranInfo;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.view.AyahToolBar;
import com.quran.labs.androidquran.view.HighlightingImageView;
import com.quran.labs.androidquran.view.QuranPageLayout;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.Set;

import androidx.annotation.NonNull;

public class AyahScrollableImageTrackerItem extends AyahImageTrackerItem {
  @NonNull private final QuranPageLayout quranPageLayout;
  private final int screenHeight;

  public AyahScrollableImageTrackerItem(int page,
                                        int screenHeight,
                                        QuranInfo quranInfo,
                                        QuranDisplayData quranDisplayData,
                                        @NonNull QuranPageLayout quranPageLayout,
                                        @NonNull Set<ImageDrawHelper> imageDrawHelpers,
                                        @NonNull HighlightingImageView highlightingImageView) {
    super(page, screenHeight, quranInfo, quranDisplayData, imageDrawHelpers, highlightingImageView);
    this.screenHeight = screenHeight;
    this.quranPageLayout = quranPageLayout;
  }

  @Override
  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (this.page == page && scrollToAyah && coordinates != null) {
      final RectF highlightBounds = ImageAyahUtils.getYBoundsForHighlight(coordinates, sura, ayah);
      if (highlightBounds != null) {
        Matrix matrix = ayahView.getImageMatrix();
        matrix.mapRect(highlightBounds);

        int currentScrollY = quranPageLayout.getCurrentScrollY();
        final boolean topOnScreen = highlightBounds.top > currentScrollY &&
            highlightBounds.top < currentScrollY + screenHeight;
        final boolean bottomOnScreen = highlightBounds.bottom > currentScrollY &&
            highlightBounds.bottom < currentScrollY + screenHeight;
        final boolean encompassesScreen = highlightBounds.top < currentScrollY &&
            highlightBounds.bottom > currentScrollY + screenHeight;

        final boolean canEntireAyahBeVisible = highlightBounds.height() < screenHeight;

        // scroll when:
        // 1. the entire ayah fits on the screen, but the top or bottom aren't on the screen
        // 2. the entire ayah won't fit on the screen and neither the top is on the screen,
        //    nor is the bottom on the screen, nor is the current ayah greater than the visible
        //    viewport of the ayah (i.e. you're not in the middle of the ayah right now).
        final boolean scroll = (canEntireAyahBeVisible && (!topOnScreen || !bottomOnScreen)) ||
            (!canEntireAyahBeVisible && !topOnScreen && !bottomOnScreen && !encompassesScreen);

        if (scroll) {
          int y = (int) highlightBounds.top - (int) (0.05 * screenHeight);
          quranPageLayout.smoothScrollLayoutTo(y);
        }
      }
    }
    return super.onHighlightAyah(page, sura, ayah, type, scrollToAyah);
  }

  @Override
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int page, int sura, int ayah, int toolBarWidth,
                                                     int toolBarHeight) {
    AyahToolBar.AyahToolBarPosition position =
        super.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight);
    if (position != null) {
      // If we're in landscape mode (wrapped in SV) update the y-offset
      position.yScroll = 0 - quranPageLayout.getCurrentScrollY();
    }
    return position;
  }
}
