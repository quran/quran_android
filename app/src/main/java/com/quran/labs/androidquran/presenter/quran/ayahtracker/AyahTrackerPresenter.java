package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.app.Activity;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.view.MotionEvent;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.HighlightInfo;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.widgets.AyahToolBar;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

@QuranPageScope
public class AyahTrackerPresenter implements AyahTracker,
    Presenter<AyahTrackerPresenter.AyahInteractionHandler> {
  private AyahTrackerItem[] items;
  private HighlightInfo pendingHighlightInfo;

  @Inject
  public AyahTrackerPresenter() {
    this.items = new AyahTrackerItem[0];
  }

  public void setPageBounds(int page, RectF bounds) {
    for (AyahTrackerItem item : items) {
      item.onSetPageBounds(page, bounds);
    }
  }

  public void setAyahCoordinates(int page, Map<String, List<AyahBounds>> coordinates) {
    for (AyahTrackerItem item : items) {
      item.onSetAyahCoordinates(page, coordinates);
    }

    if (pendingHighlightInfo != null && !coordinates.isEmpty()) {
      highlightAyah(pendingHighlightInfo.sura, pendingHighlightInfo.ayah,
          pendingHighlightInfo.highlightType, pendingHighlightInfo.scrollToAyah);
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
    int page = items.length == 1 ? items[0].page : QuranInfo.getPageFromSuraAyah(sura, ayah);
    for (AyahTrackerItem item : items) {
      handled = handled | item.onHighlightAyah(page, sura, ayah, type, scrollToAyah);
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
    int page = items.length == 1 ? items[0].page : QuranInfo.getPageFromSuraAyah(sura, ayah);
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
    int page = items.length == 1 ? items[0].page : QuranInfo.getPageFromSuraAyah(sura, ayah);
    for (AyahTrackerItem item : items) {
      AyahToolBar.AyahToolBarPosition position =
          item.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight);
      if (position != null) {
        return position;
      }
    }
    return null;
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
        (!QuranFileUtils.haveAyaPositionFile(activity) ||
            !QuranFileUtils.hasArabicSearchDatabase(activity))) {
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
