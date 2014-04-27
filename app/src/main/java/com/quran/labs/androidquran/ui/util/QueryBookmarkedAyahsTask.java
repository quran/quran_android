package com.quran.labs.androidquran.ui.util;

import android.content.Context;

import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.AsyncTask;

import java.util.ArrayList;
import java.util.List;

public class QueryBookmarkedAyahsTask extends AsyncTask<Integer, Void, List<BookmarksDBAdapter.Bookmark>> {
  private BookmarksDBAdapter mBookmarksAdapter;

  public QueryBookmarkedAyahsTask(Context context) {
    if (context != null && context instanceof PagerActivity) {
      mBookmarksAdapter = ((PagerActivity) context).getBookmarksAdapter();
    }
  }

  @Override
  protected List<BookmarksDBAdapter.Bookmark> doInBackground(Integer... params) {
    if (params == null || mBookmarksAdapter == null) {
      return null;
    }

    List<BookmarksDBAdapter.Bookmark> result = new ArrayList<BookmarksDBAdapter.Bookmark>();
    for (Integer page : params) {
      List<BookmarksDBAdapter.Bookmark> taggedAyahs = mBookmarksAdapter.getBookmarkedAyahsOnPage(page);
      result.addAll(taggedAyahs);
    }

    return result;
  }
}
