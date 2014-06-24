package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.TranslationUtils;
import com.quran.labs.androidquran.widgets.TranslationView;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import java.util.List;

public class AyahTranslationFragment extends AyahActionFragment {
  private static final String TAG = "AyahTranslationFragment";

  private ProgressBar mProgressBar;
  private TranslationView mTranslationView;
  private View mEmptyState;
  private AsyncTask mCurrentTask;

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(
        R.layout.translation_panel, container, false);

    mTranslationView =
        (TranslationView) view.findViewById(R.id.translation_view);
    mTranslationView.setIsInAyahActionMode(true);
    mProgressBar = (ProgressBar) view.findViewById(R.id.progress);
    mEmptyState = view.findViewById(R.id.empty_state);
    final Button getTranslations =
        (Button) view.findViewById(R.id.get_translations_button);
    getTranslations.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final Activity activity = getActivity();
        if (activity instanceof PagerActivity) {
          ((PagerActivity) activity).startTranslationManager();
        }
      }
    });
    return view;
  }

  @Override
  public void refreshView() {
    if (mStart == null || mEnd == null) { return; }

    final Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      String db = TranslationUtils.getDefaultTranslation(activity,
          ((PagerActivity) activity).getTranslations());
      if (db == null) {
        mProgressBar.setVisibility(View.GONE);
        mEmptyState.setVisibility(View.VISIBLE);
        return;
      }

      Integer[] bounds = new Integer[]{ mStart.sura,
          mStart.ayah, mEnd.sura, mEnd.ayah };
      if (mCurrentTask != null) {
        mCurrentTask.cancel(true);
      }
      mCurrentTask = new ShowTafsirTask(activity, bounds, db).execute();
    }
  }

  private class ShowTafsirTask extends TranslationTask {

    public ShowTafsirTask(Context context, Integer[] bounds, String db) {
      super(context, bounds, db);
      mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected boolean loadArabicAyahText() {
      return false;
    }

    @Override
    protected void onPostExecute(List<QuranAyah> result) {
      mProgressBar.setVisibility(View.GONE);
      if (result != null) {
        mEmptyState.setVisibility(View.GONE);
        mTranslationView.setAyahs(result);
      } else {
        mEmptyState.setVisibility(View.VISIBLE);
      }
      mCurrentTask = null;
    }
  }

}
