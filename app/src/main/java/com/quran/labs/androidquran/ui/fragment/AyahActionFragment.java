package com.quran.labs.androidquran.ui.fragment;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;

import android.os.Bundle;

public abstract class AyahActionFragment extends SherlockFragment {

  protected SuraAyah mStart;
  protected SuraAyah mEnd;
  private boolean mJustCreated;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mJustCreated = true;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (mJustCreated) {
      mJustCreated = false;
      PagerActivity activity = (PagerActivity) getActivity();
      if (activity != null) {
        mStart = activity.getSelectionStart();
        mEnd = activity.getSelectionEnd();
        refreshView();
      }
    }
  }

  public void updateAyahSelection(SuraAyah start, SuraAyah end) {
    mStart = start;
    mEnd = end;
    refreshView();
  }

  protected abstract void refreshView();

}
