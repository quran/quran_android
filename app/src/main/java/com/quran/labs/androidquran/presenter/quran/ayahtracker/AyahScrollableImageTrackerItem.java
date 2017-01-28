package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranPageLayout;

public class AyahScrollableImageTrackerItem extends AyahImageTrackerItem {
  @NonNull private QuranPageLayout quranPageLayout;

  public AyahScrollableImageTrackerItem(int page,
                                        @NonNull QuranPageLayout quranPageLayout,
                                        @NonNull HighlightingImageView highlightingImageView) {
    super(page, highlightingImageView);
    this.quranPageLayout = quranPageLayout;
  }

  @Override
  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (this.page == page && scrollToAyah && coordinates != null) {
      final RectF highlightBounds = ImageAyahUtils.getYBoundsForHighlight(coordinates, sura, ayah);
      if (highlightBounds != null) {
        int screenHeight = QuranScreenInfo.getInstance().getHeight();

        Matrix matrix = ayahView.getImageMatrix();
        matrix.mapRect(highlightBounds);

        int currentScrollY = quranPageLayout.getCurrentScrollY();
        final boolean topOnScreen = highlightBounds.top > currentScrollY &&
            highlightBounds.top < currentScrollY + screenHeight;
        final boolean bottomOnScreen = highlightBounds.bottom > currentScrollY &&
            highlightBounds.bottom < currentScrollY + screenHeight;

        if (!topOnScreen || !bottomOnScreen) {
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
