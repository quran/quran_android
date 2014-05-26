package com.quran.labs.androidquran.ui.fragment;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;

public abstract class AyahActionFragment extends SherlockFragment {

  protected SuraAyah mStart;
  protected SuraAyah mEnd;

  @Override
  public void onResume() {
    super.onResume();
    PagerActivity activity = (PagerActivity) getActivity();
    if (activity != null) {
      mStart = activity.getSelectionStart();
      mEnd = activity.getSelectionEnd();
      refreshView();
    }
  }

  public void updateAyahSelection(SuraAyah start, SuraAyah end) {
    mStart = start;
    mEnd = end;
    refreshView();
  }

  protected void refreshView(){
    // Subclasses should override this and refresh their view
  }

}
