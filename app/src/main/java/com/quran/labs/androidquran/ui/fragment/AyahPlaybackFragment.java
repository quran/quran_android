package com.quran.labs.androidquran.ui.fragment;


import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.audio.AudioRequest;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.util.TypefaceManager;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.view.QuranSpinner;
import com.shawnlin.numberpicker.NumberPicker;

import java.text.NumberFormat;
import java.util.Locale;

import javax.inject.Inject;

public class AyahPlaybackFragment extends AyahActionFragment {
  private static final int ITEM_LAYOUT = R.layout.sherlock_spinner_item;
  private static final int ITEM_DROPDOWN_LAYOUT = R.layout.sherlock_spinner_dropdown_item;
  private static final int MAX_REPEATS = 25;

  private SuraAyah decidedStart;
  private SuraAyah decidedEnd;
  private boolean shouldEnforce;
  private int rangeRepeatCount;
  private int verseRepeatCount;
  final int defaultVerseRepeat = 1;
  final int defaultRangeRepeat = 1;

  private Button applyButton;
  private QuranSpinner startSuraSpinner;
  private QuranSpinner startAyahSpinner;
  private QuranSpinner endingSuraSpinner;
  private QuranSpinner endingAyahSpinner;
  private NumberPicker repeatVersePicker;
  private NumberPicker repeatRangePicker;
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
    restrictToRange = view.findViewById(R.id.restrict_to_range);
    applyButton = view.findViewById(R.id.apply);
    applyButton.setOnClickListener(mOnClickListener);
    repeatVersePicker = view.findViewById(R.id.repeat_verse_picker);
    repeatRangePicker = view.findViewById(R.id.repeat_range_picker);

    final Context context = requireContext();

    boolean isArabicNames = QuranSettings.getInstance(context).isArabicNames();
    final Locale locale;
    if (isArabicNames) {
      locale = new Locale("ar");
    } else {
      locale = Locale.getDefault();
    }
    final NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);

    final String[] values = new String[MAX_REPEATS + 1];
    for (int i = 1; i <= MAX_REPEATS; i++) {
      values[i - 1] = numberFormat.format(i);
    }
    values[MAX_REPEATS] = getString(R.string.infinity);

    if (isArabicNames) {
      repeatVersePicker.setFormatter(this::arFormat);
      repeatRangePicker.setFormatter(this::arFormat);
      Typeface typeface = TypefaceManager.getHeaderFooterTypeface(context);
      repeatVersePicker.setTypeface(typeface);
      repeatVersePicker.setSelectedTypeface(typeface);
      repeatRangePicker.setTypeface(typeface);
      repeatRangePicker.setSelectedTypeface(typeface);
      // Use larger text size since KFGQPC font is small
      repeatVersePicker.setSelectedTextSize(R.dimen.arabic_number_picker_selected_text_size);
      repeatRangePicker.setSelectedTextSize(R.dimen.arabic_number_picker_selected_text_size);
      repeatVersePicker.setTextSize(R.dimen.arabic_number_picker_text_size);
      repeatRangePicker.setTextSize(R.dimen.arabic_number_picker_text_size);
    }
    repeatVersePicker.setMinValue(1);
    repeatVersePicker.setMaxValue(MAX_REPEATS + 1);
    repeatRangePicker.setMinValue(1);
    repeatRangePicker.setMaxValue(MAX_REPEATS + 1);
    repeatVersePicker.setDisplayedValues(values);
    repeatRangePicker.setDisplayedValues(values);
    repeatRangePicker.setValue(defaultRangeRepeat);
    repeatVersePicker.setValue(defaultVerseRepeat);
    repeatRangePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
      if (newVal > 1) {
        // whenever we want to repeat the range, we have to enable restrictToRange
        restrictToRange.setChecked(true);
      }
    });

    startAyahAdapter = initializeAyahSpinner(context, startAyahSpinner);
    endingAyahAdapter = initializeAyahSpinner(context, endingAyahSpinner);
    initializeSuraSpinner(context, startSuraSpinner, startAyahAdapter);
    initializeSuraSpinner(context, endingSuraSpinner, endingAyahAdapter);

    final String[] repeatOptions = context.getResources().getStringArray(R.array.repeatValues);
    final ArrayAdapter<CharSequence> rangeAdapter =
        new ArrayAdapter<>(context, ITEM_LAYOUT, repeatOptions);
    rangeAdapter.setDropDownViewResource(
        ITEM_DROPDOWN_LAYOUT);

    final ArrayAdapter<CharSequence> verseAdapter =
        new ArrayAdapter<>(context, ITEM_LAYOUT, repeatOptions);
    verseAdapter.setDropDownViewResource(
        ITEM_DROPDOWN_LAYOUT);
    return view;
  }

  private String arFormat(int value) {
    NumberFormat numberFormat = NumberFormat.getNumberInstance(new Locale("ar"));
    return numberFormat.format(value);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
  }

  private final View.OnClickListener mOnClickListener = v -> {
    if (v.getId() == R.id.apply) {
      apply();
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
      int repeatVerse = repeatVersePicker.getValue() - 1;
      int repeatRange = repeatRangePicker.getValue() - 1;

      if (repeatVerse == MAX_REPEATS) {
        repeatVerse = -1;
      }

      if (repeatRange == MAX_REPEATS) {
        repeatRange = -1;
      }

      // Overwrite if infinite checkbox is checked
      final int verseRepeat = repeatVerse;
      final int rangeRepeat = repeatRange;
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
            rangeRepeat, enforceRange);
      } else if (shouldEnforce != enforceRange ||
          rangeRepeatCount != rangeRepeat ||
          verseRepeatCount != verseRepeat) {
        // can just update repeat settings
        if (!pagerActivity.updatePlayOptions(
            rangeRepeat, verseRepeat, enforceRange)) {
          // audio stopped in the process, let's start it
          pagerActivity.playFromAyah(currentStart, currentEnding, page, verseRepeat,
              rangeRepeat, enforceRange);
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
    for (int i = 0; i < suras.length; i++) {
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
            int ayahCount = quranInfo.getNumberOfAyahs(sura);
            CharSequence[] ayahs = new String[ayahCount];
            for (int i = 0; i < ayahCount; i++) {
              ayahs[i] = QuranUtils.getLocalizedNumber(context, (i + 1));
            }
            ayahAdapter.clear();

            for (int i = 0; i < ayahCount; i++) {
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

      final int maxAyat = quranInfo.getNumberOfAyahs(start.sura);
      if (maxAyat == -1) {
        return;
      }

      updateAyahSpinner(startAyahSpinner, startAyahAdapter, maxAyat, start.ayah);
      final int endAyat = (ending.sura == start.sura) ? maxAyat :
          quranInfo.getNumberOfAyahs(ending.sura);
      updateAyahSpinner(endingAyahSpinner, endingAyahAdapter,
          endAyat, ending.ayah);
      startSuraSpinner.setSelection(start.sura - 1);
      endingSuraSpinner.setSelection(ending.sura - 1);
      restrictToRange.setChecked(shouldEnforce);
      repeatRangePicker.setValue(rangeRepeatCount + 1);
      repeatVersePicker.setValue(verseRepeatCount + 1);
    }
  }
}
