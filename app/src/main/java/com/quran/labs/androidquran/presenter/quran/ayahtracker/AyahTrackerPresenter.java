package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import android.graphics.RectF;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.HighlightInfo;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.widgets.AyahToolBar;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AyahTrackerPresenter implements AyahTracker,
    Presenter<AyahTrackerPresenter.AyahInteractionHandler> {
  private AyahTrackerItem[] items;
  private HighlightInfo pendingHighlightInfo;

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

  public QuranAyah getAyahForPosition(int page, float x, float y) {
    for (AyahTrackerItem item : items) {
      QuranAyah ayah = item.getAyahForPosition(page, x, y);
      if (ayah != null) {
        return ayah;
      }
    }
    return null;
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
