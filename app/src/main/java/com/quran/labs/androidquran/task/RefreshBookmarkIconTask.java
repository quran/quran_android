package com.quran.labs.androidquran.task;

import android.app.Activity;

import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;

public class RefreshBookmarkIconTask extends PagerActivityTask<Void, Void, Boolean> {
  private SuraAyah mSuraAyah;

  public RefreshBookmarkIconTask(PagerActivity activity, SuraAyah suraAyah) {
    super(activity);
    mSuraAyah = suraAyah;
  }

  @Override
  protected Boolean doInBackground(Void... params) {
    if (mSuraAyah == null) return null;
    BookmarksDBAdapter adapter = null;
    Activity activity = getActivity();
    if (activity != null && activity instanceof BookmarkHandler){
      adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
    }

    if (adapter == null){ return null; }

    boolean bookmarked = adapter.getBookmarkId(
        mSuraAyah.sura, mSuraAyah.ayah, mSuraAyah.getPage()) >= 0;
    return bookmarked;
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);
    PagerActivity activity = getActivity();
    if (result != null && activity != null){
      activity.updateAyahBookmarkIcon(mSuraAyah, result);
    }
  }

}
