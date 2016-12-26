package com.quran.labs.androidquran.presenter.quran;

import android.app.Activity;
import android.view.MotionEvent;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranFileUtils;

public class QuranPageTouchUtil {

  public static boolean handleTouchEvent(Activity activity, MotionEvent event,
                                         AyahSelectedListener.EventType eventType,
                                         int page, AyahTrackerPresenter ayahTrackerPresenter,
                                         AyahSelectedListener ayahSelectedListener,
                                         boolean ayahCoordinatesError) {
    if (eventType == AyahSelectedListener.EventType.DOUBLE_TAP) {
      ayahTrackerPresenter.unHighlightAyahs(HighlightType.SELECTION);
    } else if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (ayahCoordinatesError) {
        checkCoordinateData(activity);
      } else {
        handlePress(event, eventType, page, ayahTrackerPresenter, ayahSelectedListener);
      }
      return true;
    }
    return ayahSelectedListener.onClick(eventType);
  }

  private static void handlePress(MotionEvent ev, AyahSelectedListener.EventType eventType,
                                  int pageNumber, AyahTrackerPresenter ayahTrackerPresenter,
                                  AyahSelectedListener ayahSelectedListener) {
    QuranAyah result = ayahTrackerPresenter.getAyahForPosition(pageNumber, ev.getX(), ev.getY());
    if (result != null && ayahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      ayahSelectedListener.onAyahSelected(eventType, suraAyah, ayahTrackerPresenter);
    }
  }

  private static void checkCoordinateData(Activity activity) {
    if (activity instanceof PagerActivity &&
        (!QuranFileUtils.haveAyaPositionFile(activity) ||
            !QuranFileUtils.hasArabicSearchDatabase(activity))) {
      PagerActivity pagerActivity = (PagerActivity) activity;
      pagerActivity.showGetRequiredFilesDialog();
    }
  }
}
