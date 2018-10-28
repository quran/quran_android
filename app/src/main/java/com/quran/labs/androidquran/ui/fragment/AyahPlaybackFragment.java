package com.quran.labs.androidquran.ui.fragment;


import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.audio.AudioRequest;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.QuranSpinner;

import javax.inject.Inject;

public class AyahPlaybackFragment extends AyahActionFragment {
  private static final int REPEAT_MAX = 3;
  private static final int ITEM_LAYOUT = R.layout.sherlock_spinner_item;
  private static final int ITEM_DROPDOWN_LAYOUT = R.layout.sherlock_spinner_dropdown_item;

  private SuraAyah decidedStart;
  private SuraAyah decidedEnd;
  private boolean shouldEnforce;
  private int rangeRepeatCount;
  private int verseRepeatCount;

  private Button applyButton;
  private QuranSpinner startSuraSpinner;
  private QuranSpinner startAyahSpinner;
  private QuranSpinner endingSuraSpinner;
  private QuranSpinner endingAyahSpinner;
  private QuranSpinner repeatVerseSpinner;
  private QuranSpinner repeatRangeSpinner;
  private CheckBox restrictToRange;
  private ArrayAdapter<CharSequence> startAyahAdapter;
  private ArrayAdapter<CharSequence> endingAyahAdapter;
  
  @Inject QuranInfo quranInfo;

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container,
                           Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.audio_panel, container, false);
    view.setOnClickListener(mOnClickListener);

    startSuraSpinner = view.findViewById(R.id.start_sura_spinner);
    startAyahSpinner = view.findViewById(R.id.start_ayah_spinner);
    endingSuraSpinner = view.findViewById(R.id.end_sura_spinner);
    endingAyahSpinner = view.findViewById(R.id.end_ayah_spinner);
    repeatVerseSpinner = view.findViewById(R.id.repeat_verse_spinner);
    repeatRangeSpinner = view.findViewById(R.id.repeat_range_spinner);
    restrictToRange = view.findViewById(R.id.restrict_to_range);
    applyButton = view.findViewById(R.id.apply);
    applyButton.setOnClickListener(mOnClickListener);

    final Context context = getActivity();
    startAyahAdapter = initializeAyahSpinner(context, startAyahSpinner);
    endingAyahAdapter = initializeAyahSpinner(context, endingAyahSpinner);
    initializeSuraSpinner(context, startSuraSpinner, startAyahAdapter);
    initializeSuraSpinner(context, endingSuraSpinner, endingAyahAdapter);

    final String[] repeatOptions = context.getResources().getStringArray(R.array.repeatValues);
    final ArrayAdapter<CharSequence> rangeAdapter =
        new ArrayAdapter<>(context, ITEM_LAYOUT, repeatOptions);
    rangeAdapter.setDropDownViewResource(
        ITEM_DROPDOWN_LAYOUT);
    repeatRangeSpinner.setAdapter(rangeAdapter);
    repeatRangeSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateEnforceBounds(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
    final ArrayAdapter<CharSequence> verseAdapter =
        new ArrayAdapter<>(context, ITEM_LAYOUT, repeatOptions);
    verseAdapter.setDropDownViewResource(
        ITEM_DROPDOWN_LAYOUT);
    repeatVerseSpinner.setAdapter(verseAdapter);
    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
  }

  private View.OnClickListener mOnClickListener = v -> {
    switch (v.getId()) {
      case R.id.apply: {
        apply();
        break;
      }
    }
  };

  private void apply() {
    final Context context = getActivity();
    if (context instanceof PagerActivity) {
      final SuraAyah start = new SuraAyah(
          startSuraSpinner.getSelectedItemPosition() + 1,
          startAyahSpinner.getSelectedItemPosition() + 1);
      final SuraAyah ending = new SuraAyah(
          endingSuraSpinner.getSelectedItemPosition() + 1,
          endingAyahSpinner.getSelectedItemPosition() + 1);

      // force the correct order
      final SuraAyah currentStart;
      final SuraAyah currentEnding;
      if (ending.after(start)) {
        currentStart = start;
        currentEnding = ending;
      } else {
        currentStart = ending;
        currentEnding = start;
      }

      final int page = quranInfo.getPageFromSuraAyah(currentStart.sura, currentStart.ayah);
      final int verseRepeat = positionToRepeat(
          repeatVerseSpinner.getSelectedItemPosition());
      final int rangeRepeat = positionToRepeat(
          repeatRangeSpinner.getSelectedItemPosition());
      final boolean enforceRange = restrictToRange.isChecked();

      boolean updatedRange = false;
      final PagerActivity pagerActivity = (PagerActivity) context;
      if (!currentStart.equals(decidedStart) ||
          !currentEnding.equals(decidedEnd)) {
        // different range or not playing, so make a new request
        updatedRange = true;
        if (this.start != null) {
          final SuraAyah starting = decidedStart == null ? this.start : decidedStart;
          final int origPage = quranInfo.getPageFromSuraAyah(starting.sura, starting.ayah);
          if (page != origPage) {
            pagerActivity.highlightAyah(currentStart.sura,
                currentStart.ayah, HighlightType.AUDIO);
          }
        }
        pagerActivity.playFromAyah(currentStart, currentEnding, page, verseRepeat,
            rangeRepeat, enforceRange, true);
      } else if (shouldEnforce != enforceRange ||
          rangeRepeatCount != rangeRepeat ||
          verseRepeatCount != verseRepeat) {
        // can just update repeat settings
        if (!pagerActivity.updatePlayOptions(
            rangeRepeat, verseRepeat, enforceRange)) {
          // audio stopped in the process, let's start it
          pagerActivity.playFromAyah(currentStart, currentEnding, page, verseRepeat,
              rangeRepeat, enforceRange, true);
        }
      }
      pagerActivity.endAyahMode();
      if (updatedRange) {
        pagerActivity.toggleActionBarVisibility(true);
      }
    }
  }

  private void initializeSuraSpinner(final Context context,
                                     QuranSpinner spinner,
                                     final ArrayAdapter<CharSequence> ayahAdapter) {
    String[] suras = context.getResources().
        getStringArray(R.array.sura_names);
    for (int i=0; i<suras.length; i++){
      suras[i] = QuranUtils.getLocalizedNumber(context, (i + 1)) +
          ". " + suras[i];
    }
    ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(context, ITEM_LAYOUT, suras);
    adapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT);
    spinner.setAdapter(adapter);

    spinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
            int sura = position + 1;
            int ayahCount = quranInfo.getNumAyahs(sura);
            CharSequence[] ayahs = new String[ayahCount];
            for (int i = 0; i < ayahCount; i++){
              ayahs[i] = QuranUtils.getLocalizedNumber(context, (i + 1));
            }
            ayahAdapter.clear();

            for (int i=0; i<ayahCount; i++){
              ayahAdapter.add(ayahs[i]);
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> arg0) {
          }
        });
  }

  private ArrayAdapter<CharSequence> initializeAyahSpinner(
      Context context, QuranSpinner spinner) {
    final ArrayAdapter<CharSequence> ayahAdapter = new ArrayAdapter<>(context, ITEM_LAYOUT);
    ayahAdapter.setDropDownViewResource(ITEM_DROPDOWN_LAYOUT);
    spinner.setAdapter(ayahAdapter);
    return ayahAdapter;
  }

  private void updateAyahSpinner(QuranSpinner spinner,
                                 ArrayAdapter<CharSequence> adapter,
                                 int maxAyah,
                                 int currentAyah) {
    final Context context = getActivity();
    if (context != null) {
      CharSequence[] ayahs = new String[maxAyah];
      for (int i = 0; i < maxAyah; i++) {
        ayahs[i] = QuranUtils.getLocalizedNumber(context, (i + 1));
      }
      adapter.clear();

      for (int i = 0; i < maxAyah; i++) {
        adapter.add(ayahs[i]);
      }
      spinner.setSelection(currentAyah - 1);
    }
  }

  private void updateEnforceBounds(int rangeRepeatPosition) {
    if (rangeRepeatPosition > 0) {
      restrictToRange.setChecked(true);
      restrictToRange.setEnabled(false);
    } else {
      restrictToRange.setEnabled(true);
    }
  }

  private int repeatToPosition(int repeat) {
    if (repeat == -1) {
      return REPEAT_MAX;
    } else {
      return repeat;
    }
  }

  private int positionToRepeat(int position) {
    if (position >= REPEAT_MAX) {
      return -1;
    } else {
      return position;
    }
  }

  @Override
  protected void refreshView() {
    final Context context = getActivity();
    if (context instanceof PagerActivity && start != null && end != null) {
      final AudioRequest lastRequest =
          ((PagerActivity) context).getLastAudioRequest();
      final SuraAyah start;
      final SuraAyah ending;
      if (lastRequest != null) {
        start = lastRequest.getStart();
        ending = lastRequest.getEnd();
        verseRepeatCount = lastRequest.getRepeatInfo();
        rangeRepeatCount = lastRequest.getRangeRepeatInfo();
        shouldEnforce = lastRequest.getEnforceBounds();
        decidedStart = start;
        decidedEnd = ending;
        applyButton.setText(R.string.play_apply);
      } else {
        start = this.start;
        if (this.start.equals(end)) {
          final int startPage = quranInfo.getPageFromSuraAyah(start.sura, start.ayah);
          final int[] pageBounds = quranInfo.getPageBounds(startPage);
          ending = new SuraAyah(pageBounds[2], pageBounds[3]);
          shouldEnforce = false;
        } else {
          ending = end;
          shouldEnforce = true;
        }
        rangeRepeatCount = 0;
        verseRepeatCount = 0;
        decidedStart = null;
        decidedEnd = null;
        applyButton.setText(R.string.play_apply_and_play);
      }

      final int maxAyat = quranInfo.getNumAyahs(start.sura);
      if (maxAyat == -1) {
        return;
      }

      updateAyahSpinner(startAyahSpinner, startAyahAdapter, maxAyat, start.ayah);
      final int endAyat = (ending.sura == start.sura) ? maxAyat :
          quranInfo.getNumAyahs(ending.sura);
      updateAyahSpinner(endingAyahSpinner, endingAyahAdapter,
          endAyat, ending.ayah);
      startSuraSpinner.setSelection(start.sura - 1);
      endingSuraSpinner.setSelection(ending.sura - 1);
      repeatRangeSpinner.setSelection(repeatToPosition(rangeRepeatCount));
      repeatVerseSpinner.setSelection(repeatToPosition(verseRepeatCount));
      restrictToRange.setChecked(shouldEnforce);
    }
  }
}
