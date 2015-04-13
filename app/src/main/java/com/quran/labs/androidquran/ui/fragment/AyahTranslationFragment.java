package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter;
import com.quran.labs.androidquran.widgets.TranslationView;
import com.quran.labs.androidquran.widgets.spinner.AdapterViewCompat;
import com.quran.labs.androidquran.widgets.spinner.SpinnerCompat;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import java.util.List;

public class AyahTranslationFragment extends AyahActionFragment {

  private ProgressBar mProgressBar;
  private TranslationView mTranslationView;
  private View mEmptyState;
  private AsyncTask mCurrentTask;
  private TranslationItem mTranslationItem;
  private View mTranslationControls;
  private SpinnerCompat mTranslator;
  private TranslationsSpinnerAdapter mTranslationAdapter;
  private List<TranslationItem> mTranslations;

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(
        R.layout.translation_panel, container, false);

    mTranslator = (SpinnerCompat) view.findViewById(R.id.translator);
    mTranslationView =
        (TranslationView) view.findViewById(R.id.translation_view);
    mTranslationView.setIsInAyahActionMode(true);
    mProgressBar = (ProgressBar) view.findViewById(R.id.progress);
    mEmptyState = view.findViewById(R.id.empty_state);
    mTranslationControls = view.findViewById(R.id.controls);
    final View next = mTranslationControls.findViewById(R.id.next_ayah);
    next.setOnClickListener(mOnClickListener);

    final View prev = mTranslationControls.findViewById(R.id.previous_ayah);
    prev.setOnClickListener(mOnClickListener);

    final Button getTranslations =
        (Button) view.findViewById(R.id.get_translations_button);
    getTranslations.setOnClickListener(mOnClickListener);
    return view;
  }

  private View.OnClickListener mOnClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      final Activity activity = getActivity();
      final PagerActivity pagerActivity;
      if (activity instanceof PagerActivity) {
        pagerActivity = (PagerActivity) activity;
      } else {
        return;
      }

      switch (v.getId()) {
        case R.id.get_translations_button:
          pagerActivity.startTranslationManager();
          break;
        case R.id.next_ayah:
          pagerActivity.nextAyah();
          break;
        case R.id.previous_ayah:
          pagerActivity.previousAyah();
          break;
      }
    }
  };

  public void refreshView() {
    if (mStart == null || mEnd == null) { return; }

    final Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      PagerActivity pagerActivity = (PagerActivity) activity;
      if (mTranslations == null) {
        mTranslations = pagerActivity.getTranslations();
      }

      if (mTranslations == null || mTranslations.size() == 0) {
        mProgressBar.setVisibility(View.GONE);
        mEmptyState.setVisibility(View.VISIBLE);
        mTranslationControls.setVisibility(View.GONE);
        return;
      }

      if (mTranslationAdapter == null) {
        mTranslationAdapter = new TranslationsSpinnerAdapter(activity,
            R.layout.sherlock_spinner_dropdown_item,
            pagerActivity.getTranslationNames(), mTranslations);
        mTranslator.setAdapter(mTranslationAdapter);
        mTranslator.setOnItemSelectedListener(new AdapterViewCompat.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterViewCompat<?> parent, View view, int position,
              long id) {
            TranslationItem item = mTranslationAdapter.getTranslationItem(position);
            if (!item.filename.equals(mTranslationItem.filename)) {
              PreferenceManager.getDefaultSharedPreferences(activity).edit()
                  .putString(Constants.PREF_ACTIVE_TRANSLATION, item.filename).commit();
              refreshView();
            }
          }

          @Override
          public void onNothingSelected(AdapterViewCompat<?> parent) {
          }
        });
      }

      if (mStart.equals(mEnd)) {
        mTranslationControls.setVisibility(View.VISIBLE);
      } else {
        mTranslationControls.setVisibility(View.GONE);
      }

      Integer[] bounds = new Integer[]{ mStart.sura,
          mStart.ayah, mEnd.sura, mEnd.ayah };
      if (mCurrentTask != null) {
        mCurrentTask.cancel(true);
      }

      int pos = mTranslationAdapter.getPositionForActiveTranslation();
      mTranslationItem = mTranslationAdapter.getTranslationItem(pos);
      mTranslator.setSelection(pos);
      mCurrentTask = new ShowTafsirTask(activity, bounds,
          mTranslationItem.filename).execute();
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
