package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.app.Activity;
import androidx.annotation.Nullable;
import android.view.MotionEvent;

import com.quran.data.core.QuranInfo;
import com.quran.labs.androidquran.common.HighlightInfo;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.view.AyahToolBar;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

@QuranPageScope
public class AyahTrackerPresenter implements AyahTracker,
    Presenter<AyahTrackerPresenter.AyahInteractionHandler> {
  private AyahTrackerItem[] items;
  private HighlightInfo pendingHighlightInfo;
  private final QuranInfo quranInfo;
  private final QuranFileUtils quranFileUtils;

  @Inject
  public AyahTrackerPresenter(QuranInfo quranInfo, QuranFileUtils quranFileUtils) {
    this.items = new AyahTrackerItem[0];
    this.quranInfo = quranInfo;
    this.quranFileUtils = quranFileUtils;
  }

  public void setPageBounds(PageCoordinates pageCoordinates) {
    for (AyahTrackerItem item : items) {
      item.onSetPageBounds(pageCoordinates);
    }
  }

  public void setAyahCoordinates(AyahCoordinates ayahCoordinates) {
    for (AyahTrackerItem item : items) {
      item.onSetAyahCoordinates(ayahCoordinates);
    }

    if (pendingHighlightInfo != null && !ayahCoordinates.getAyahCoordinates().isEmpty()) {
      highlightAyah(pendingHighlightInfo.getSura(), pendingHighlightInfo.getAyah(),
          pendingHighlightInfo.getHighlightType(), pendingHighlightInfo.getScrollToAyah());
    }
  }

  public void setAyahBookmarks(List<Bookmark> bookmarks) {
    for (AyahTrackerItem item : items) {
      item.onSetAyahBookmarks(bookmarks);
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    boolean handled = false;
    int page = items.length == 1 ? items[0].page : quranInfo.getPageFromSuraAyah(sura, ayah);
    for (AyahTrackerItem item : items) {
      handled = handled || item.onHighlightAyah(page, sura, ayah, type, scrollToAyah);
    }

    if (!handled) {
      pendingHighlightInfo = new HighlightInfo(sura, ayah, type, scrollToAyah);
    } else {
      pendingHighlightInfo = null;
    }
  }

  @Override
  public void highlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
    for (AyahTrackerItem item : items) {
      item.onHighlightAyat(page, ayahKeys, type);
    }
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    int page = items.length == 1 ? items[0].page : quranInfo.getPageFromSuraAyah(sura, ayah);
    for (AyahTrackerItem item : items) {
      item.onUnHighlightAyah(page, sura, ayah, type);
    }
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    for (AyahTrackerItem item : items) {
      item.onUnHighlightAyahType(type);
    }
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
                                                            int toolBarWidth, int toolBarHeight) {
    int page = items.length == 1 ? items[0].page : quranInfo.getPageFromSuraAyah(sura, ayah);
    for (AyahTrackerItem item : items) {
      AyahToolBar.AyahToolBarPosition position =
          item.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight);
      if (position != null) {
        return position;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    for (AyahTrackerItem item : items) {
      final QuranAyahInfo quranAyahInfo = item.getQuranAyahInfo(sura, ayah);
      if (quranAyahInfo != null) {
        return quranAyahInfo;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public LocalTranslation[] getLocalTranslations() {
    for (AyahTrackerItem item : items) {
      final LocalTranslation[] localTranslations = item.getLocalTranslations();
      if (localTranslations != null) {
        return localTranslations;
      }
    }
    return null;
  }

  public void handleLongClick(SuraAyah suraAyah, AyahSelectedListener ayahSelectedListener) {
    ayahSelectedListener.onAyahSelected(AyahSelectedListener.EventType.LONG_PRESS, suraAyah, this);
  }

  public void endAyahMode(AyahSelectedListener ayahSelectedListener) {
    ayahSelectedListener.endAyahMode();
  }

  public void requestMenuPositionUpdate(AyahSelectedListener ayahSelectedListener) {
    ayahSelectedListener.requestMenuPositionUpdate(this);
  }

  public boolean handleTouchEvent(Activity activity, MotionEvent event,
                                  AyahSelectedListener.EventType eventType, int page,
                                  AyahSelectedListener ayahSelectedListener,
                                  boolean ayahCoordinatesError) {
    if (eventType == AyahSelectedListener.EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    } else if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (ayahCoordinatesError) {
        checkCoordinateData(activity);
      } else {
        handlePress(event, eventType, page, ayahSelectedListener);
      }
      return true;
    }
    return ayahSelectedListener.onClick(eventType);
  }

  private void handlePress(MotionEvent ev, AyahSelectedListener.EventType eventType, int page,
                           AyahSelectedListener ayahSelectedListener) {
    SuraAyah result = getAyahForPosition(page, ev.getX(), ev.getY());
    if (result != null && ayahSelectedListener != null) {
      ayahSelectedListener.onAyahSelected(eventType, result, this);
    }
  }

  @Nullable
  private SuraAyah getAyahForPosition(int page, float x, float y) {
    for (AyahTrackerItem item : items) {
      SuraAyah ayah = item.getAyahForPosition(page, x, y);
      if (ayah != null) {
        return ayah;
      }
    }
    return null;
  }

  private void checkCoordinateData(Activity activity) {
    if (activity instanceof PagerActivity &&
        (!quranFileUtils.haveAyaPositionFile(activity) ||
            !quranFileUtils.hasArabicSearchDatabase())) {
      PagerActivity pagerActivity = (PagerActivity) activity;
      pagerActivity.showGetRequiredFilesDialog();
    }
  }

  @Override
  public void bind(AyahInteractionHandler interactionHandler) {
    this.items = interactionHandler.getAyahTrackerItems();
  }

  @Override
  public void unbind(AyahInteractionHandler interactionHandler) {
    this.items = new AyahTrackerItem[0];
  }

  public interface AyahInteractionHandler {
    AyahTrackerItem[] getAyahTrackerItems();
  }
}
