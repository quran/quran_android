package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.TranslationView;
import com.quran.labs.androidquran.widgets.QuranSpinner;

import java.util.List;

public class AyahTranslationFragment extends AyahActionFragment {

  private ProgressBar progressBar;
  private TranslationView translationView;
  private View emptyState;
  private AsyncTask currentTask;
  private LocalTranslation translationItem;
  private View translationControls;
  private QuranSpinner translator;
  private TranslationsSpinnerAdapter translationAdapter;
  private List<LocalTranslation> translations;

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(
        R.layout.translation_panel, container, false);

    translator = (QuranSpinner) view.findViewById(R.id.translator);
    translationView =
        (TranslationView) view.findViewById(R.id.translation_view);
    translationView.setIsInAyahActionMode(true);
    progressBar = (ProgressBar) view.findViewById(R.id.progress);
    emptyState = view.findViewById(R.id.empty_state);
    translationControls = view.findViewById(R.id.controls);
    final View next = translationControls.findViewById(R.id.next_ayah);
    next.setOnClickListener(onClickListener);

    final View prev = translationControls.findViewById(R.id.previous_ayah);
    prev.setOnClickListener(onClickListener);

    final Button getTranslations =
        (Button) view.findViewById(R.id.get_translations_button);
    getTranslations.setOnClickListener(onClickListener);
    return view;
  }

  private View.OnClickListener onClickListener = v -> {
    final Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      final PagerActivity pagerActivity = (PagerActivity) activity;

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
      if (translations == null) {
        translations = pagerActivity.getTranslations();
      }

      if (translations == null || translations.size() == 0) {
        progressBar.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        translationControls.setVisibility(View.GONE);
        return;
      }

      if (translationAdapter == null) {
        translationAdapter = new TranslationsSpinnerAdapter(activity,
            R.layout.support_simple_spinner_dropdown_item,
            pagerActivity.getTranslationNames(), translations);
        translator.setAdapter(translationAdapter);
        translator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            LocalTranslation item = translationAdapter.getTranslationItem(position);
            if (!item.filename.equals(translationItem.filename)) {
              QuranSettings.getInstance(activity).setActiveTranslation(item.filename);
              refreshView();
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
          }
        });
      }

      if (mStart.equals(mEnd)) {
        translationControls.setVisibility(View.VISIBLE);
      } else {
        translationControls.setVisibility(View.GONE);
      }

      int[] bounds = new int[]{ mStart.sura, mStart.ayah, mEnd.sura, mEnd.ayah };
      if (currentTask != null) {
        currentTask.cancel(true);
      }

      int pos = translationAdapter.getPositionForActiveTranslation();
      translationItem = translationAdapter.getTranslationItem(pos);
      translator.setSelection(pos);
      currentTask = new ShowTafsirTask(activity, bounds, translationItem.filename).execute();
    }
  }

  private class ShowTafsirTask extends TranslationTask {

    ShowTafsirTask(Context context, int[] bounds, String db) {
      super(context, bounds, db);
      progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected boolean loadArabicAyahText() {
      return false;
    }

    @Override
    protected void onPostExecute(List<QuranAyah> result) {
      progressBar.setVisibility(View.GONE);
      if (result != null) {
        emptyState.setVisibility(View.GONE);
        translationView.setAyahs(result);
      } else {
        emptyState.setVisibility(View.VISIBLE);
      }
      currentTask = null;
    }
  }

}
