package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.os.AsyncTask;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;

public abstract class AyahActionFragment extends SherlockFragment {

  protected SuraAyah mStart;
  protected SuraAyah mEnd;
  protected AsyncTask mCurrentTask;

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

  @Override
  public void onDestroy() {
    super.onDestroy();
    cleanup();
  }

  public void updateAyahSelection(SuraAyah start, SuraAyah end) {
    mStart = start;
    mEnd = end;
    refreshView();
  }

  protected void refreshView(){
    // Subclasses should override this and refresh their view
  }

  protected void cleanup(){
    if (mCurrentTask != null){
      mCurrentTask.cancel(true);
      mCurrentTask = null;
    }
  }

}
