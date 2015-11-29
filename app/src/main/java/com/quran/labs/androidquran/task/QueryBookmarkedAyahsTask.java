package com.quran.labs.androidquran.task;

import android.content.Context;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.PagerActivity;

import java.util.ArrayList;
import java.util.List;

public class QueryBookmarkedAyahsTask extends AsyncTask<Integer, Void, List<Bookmark>> {
  private BookmarksDBAdapter mBookmarksAdapter;

  public QueryBookmarkedAyahsTask(Context context) {
    if (context != null && context instanceof PagerActivity) {
      mBookmarksAdapter = ((PagerActivity) context).getBookmarksAdapter();
    }
  }

  @Override
  protected List<Bookmark> doInBackground(Integer... params) {
    if (params == null || mBookmarksAdapter == null) {
      return null;
    }

    List<Bookmark> result = new ArrayList<Bookmark>();
    for (Integer page : params) {
      List<Bookmark> taggedAyahs = mBookmarksAdapter.getBookmarkedAyahsOnPage(page);
      result.addAll(taggedAyahs);
    }

    return result;
  }
}
