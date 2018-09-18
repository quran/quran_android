package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.graphics.Matrix;
import android.graphics.RectF;
import androidx.annotation.NonNull;

import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranPageLayout;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.Set;

public class AyahScrollableImageTrackerItem extends AyahImageTrackerItem {
  @NonNull private QuranPageLayout quranPageLayout;
  private final int screenHeight;

  public AyahScrollableImageTrackerItem(int page,
                                        int screenHeight,
                                        QuranInfo quranInfo,
                                        @NonNull QuranPageLayout quranPageLayout,
                                        @NonNull Set<ImageDrawHelper> imageDrawHelpers,
                                        @NonNull HighlightingImageView highlightingImageView) {
    super(page, screenHeight, quranInfo, imageDrawHelpers, highlightingImageView);
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
