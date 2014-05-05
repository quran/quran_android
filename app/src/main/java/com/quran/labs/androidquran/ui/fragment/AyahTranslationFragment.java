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
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.util.TranslationTask;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;

public class AyahTranslationFragment extends SherlockFragment {
  private static final String TAG = "AyahTranslationFragment";

  private AsyncTask mCurrentTask;
  private String mText;
  private TranslationView mTranslationView;
  private int startSura, startAyah, endSura, endAyah;

  // do not remove - this is required when resuming from onSaveInstanceState
  public AyahTranslationFragment(){
  }

  public AyahTranslationFragment(int sura, int ayah){
    startSura = endSura = sura;
    startAyah = endAyah = ayah;
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
    updateAyahSelection(startSura, startAyah, endSura, endAyah);
  }

  public void updateAyahSelection(int startSura, int startAyah, int endSura, int endAyah) {
    final Activity activity = getActivity();
    if (activity == null || !(activity instanceof PagerActivity)) return;
    String db = TranslationUtils.getDefaultTranslation(activity,
        ((PagerActivity)activity).getTranslations());
    if (db == null) return;
    Integer[] bounds = new Integer[] {startSura, startAyah, endSura, endAyah};
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
