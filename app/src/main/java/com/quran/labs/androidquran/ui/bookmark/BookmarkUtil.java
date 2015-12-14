package com.quran.labs.androidquran.ui.bookmark;

import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import android.app.Activity;

import java.util.List;

public class BookmarkUtil {

  public static void handleRowClicked(Activity activity, QuranRow row) {
    if (!row.isHeader() && activity instanceof QuranActivity) {
      QuranActivity quranActivity = (QuranActivity) activity;
      if (row.isAyahBookmark()) {
        quranActivity.jumpToAndHighlight(row.page, row.sura, row.ayah);
      } else {
        quranActivity.jumpTo(row.page);
      }
    }
  }

  public static void handleTagEdit(QuranActivity activity, List<QuranRow> selected) {
    if (selected.size() == 1) {
      QuranRow row = selected.get(0);
      activity.editTag(row.tagId, row.text);
    }
  }

  public static void handleTagBookmarks(QuranActivity activity, List<QuranRow> selected) {
    long[] ids = new long[selected.size()];
    for (int i = 0, selectedItems = selected.size(); i < selectedItems; i++) {
      ids[i] = selected.get(i).bookmarkId;
    }
    activity.tagBookmarks(ids);
  }
}
