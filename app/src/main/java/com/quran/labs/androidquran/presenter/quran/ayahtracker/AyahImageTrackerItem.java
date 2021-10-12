package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.content.Context;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.data.model.selection.SelectedAyahPosition;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.view.HighlightingImageView;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  public void onSetPageBounds(PageCoordinates pageCoordinates) {
    final int page = getPage();
    if (page == pageCoordinates.getPage()) {
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
  public void onSetAyahCoordinates(AyahCoordinates ayahCoordinates) {
    final int page = getPage();
    if (page == ayahCoordinates.getPage()) {
      this.coordinates = ayahCoordinates.getAyahCoordinates();
      if (!coordinates.isEmpty()) {
        ayahView.setAyahData(ayahCoordinates);
        ayahView.invalidate();
      }
    }
  }

  @Override
  public boolean onHighlightAyah(int page, int sura, int ayah, @NonNull HighlightType type, boolean scrollToAyah) {
    if (getPage() == page && coordinates != null) {
      ayahView.highlightAyah(sura, ayah, type);
      ayahView.invalidate();
      return true;
    } else if (coordinates != null) {
      ayahView.unHighlight(type);
    }
    return false;
  }

  @Override
  public void onHighlightAyat(int page, @NonNull Set<String> ayahKeys, @NonNull HighlightType type) {
    if (getPage() == page) {
      ayahView.highlightAyat(ayahKeys, type);
      ayahView.invalidate();
    }
  }

  @Override
  public void onUnHighlightAyah(int page, int sura, int ayah, @NonNull HighlightType type) {
    if (getPage() == page) {
      ayahView.unHighlight(sura, ayah, type);
    }
  }

  @Override
  public void onUnHighlightAyahType(@NonNull HighlightType type) {
    ayahView.unHighlight(type);
  }

  @Override
  public SelectedAyahPosition getToolBarPosition(int page, int sura, int ayah, int toolBarWidth,
                                                     int toolBarHeight) {
    if (getPage() == page) {
      final List<AyahBounds> bounds = coordinates == null ? null :
          coordinates.get(sura + ":" + ayah);
      final int screenWidth = ayahView.getWidth();
      if (bounds != null && screenWidth > 0) {
        SelectedAyahPosition position =
            ImageAyahUtils.getToolBarPosition(bounds, ayahView.getImageMatrix(),
                screenWidth, screenHeight, toolBarWidth, toolBarHeight);

        final int topPadding = ayahView.getPaddingTop();
        if (topPadding > 0) {
          position = position.withY(position.getY() + topPadding);
        }

        if (isPageOnRightSide) {
          // need to adjust offset because our x is really x plus one page
          return position.withX(position.getX() + ayahView.getWidth());
        }
        return position;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public SuraAyah getAyahForPosition(int page, float x, float y) {
    return getPage() == page ?
        ImageAyahUtils.getAyahFromCoordinates(coordinates, ayahView, x, y) : null;
  }
}
