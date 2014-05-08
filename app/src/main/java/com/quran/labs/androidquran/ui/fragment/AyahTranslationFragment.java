package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.util.TranslationTask;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;

public class AyahTranslationFragment extends SherlockFragment {
  private static final String TAG = "AyahTranslationFragment";

  private AsyncTask mCurrentTask;
  private TranslationView mTranslationView;
  private SuraAyah mStart, mEnd;

  // do not remove - this is required when resuming from onSaveInstanceState
  public AyahTranslationFragment(){
  }

  public AyahTranslationFragment(SuraAyah start, SuraAyah end){
    mStart = start;
    mEnd = end;
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Activity activity = getActivity();
    mTranslationView = new TranslationView(activity);
    mTranslationView.setIsInAyahActionMode(true);
    return mTranslationView;
  }

  @Override
  public void onViewStateRestored(Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);
    if (mStart != null && mEnd != null) {
      updateAyahSelection(mStart, mEnd);
    }
  }

  public void updateAyahSelection(SuraAyah start, SuraAyah end) {
    final Activity activity = getActivity();
    if (activity == null || !(activity instanceof PagerActivity)) return;
    String db = TranslationUtils.getDefaultTranslation(activity,
        ((PagerActivity)activity).getTranslations());
    if (db == null) return;
    Integer[] bounds = new Integer[] {start.sura, start.ayah, end.sura, end.ayah};
    if (mCurrentTask != null) {
      mCurrentTask.cancel(true);
    }
    mCurrentTask = new ShowTafsirTask(activity, bounds, db).execute();
  }

  private class ShowTafsirTask extends TranslationTask {

    public ShowTafsirTask(Context context, Integer[] bounds, String db) {
      super(context, bounds, db);
    }

    @Override
    protected boolean loadArabicAyahText() {
      return false;
    }

    @Override
    protected void onPostExecute(List<QuranAyah> result) {
      if (result != null) {
        if (mTranslationView != null) {
          mTranslationView.setAyahs(result);
        }
      } else {
        // TODO show button in the fragment showing no translation, download here
        //showGetTranslationDialog();
      }
      mCurrentTask = null;
    }
  }

}
