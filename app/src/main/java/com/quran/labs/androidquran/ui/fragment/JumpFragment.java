package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.spinner.AdapterViewCompat;
import com.quran.labs.androidquran.widgets.spinner.SpinnerCompat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

public class JumpFragment extends DialogFragment {
  public static final String TAG = "JumpFragment";

  public JumpFragment() {
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Activity activity = getActivity();
    LayoutInflater inflater = activity.getLayoutInflater();
    View layout = inflater.inflate(R.layout.jump_dialog, null);

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle(activity.getString(R.string.menu_jump));

    // Sura Spinner
    final SpinnerCompat suraSpinner = (SpinnerCompat) layout.findViewById(R.id.sura_spinner);
    String[] suras = activity.getResources().getStringArray(R.array.sura_names);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i < suras.length; i++) {
      sb.append(QuranUtils.getLocalizedNumber(activity, (i + 1)));
      sb.append(". ");
      sb.append(suras[i]);
      suras[i] = sb.toString();
      sb.setLength(0);
    }
    ArrayAdapter<CharSequence> adapter =
        new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_item, suras);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    suraSpinner.setAdapter(adapter);

    // Ayah Spinner
    final SpinnerCompat ayahSpinner = (SpinnerCompat) layout.findViewById(R.id.ayah_spinner);
    final ArrayAdapter<CharSequence> ayahAdapter =
        new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_item) {
          @Override
          public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView v = (TextView) super.getDropDownView(position, convertView, parent);
            v.setGravity(Gravity.CENTER);
            return v;
          }
        };
    ayahAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    ayahSpinner.setAdapter(ayahAdapter);

    // Page text
    final EditText input = (EditText) layout.findViewById(R.id.page_number);
    input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        boolean handled = false;
        if (actionId == EditorInfo.IME_ACTION_GO) {
          Dialog dialog = getDialog();
          if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
          }
          goToPage(input.getText().toString());
          handled = true;
        }
        return handled;
      }
    });

    suraSpinner.setOnItemSelectedListener(new AdapterViewCompat.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterViewCompat<?> parent, View view, int position, long rowId) {
        Context context = getActivity();
        if (suraSpinner.getTag() == null) {
          // this is the initialization
          for (int i = 1; i <= QuranInfo.SURA_NUM_AYAHS[0]/*al-Fatiha*/; i++) {
            ayahAdapter.add(QuranUtils.getLocalizedNumber(context, i));
          }
          input.setHint(QuranUtils.getLocalizedNumber(context, 1));
//          input.setText(null); // not needed for now (redundant)
          suraSpinner.setTag(0);
        } else {
          // user interaction
          ayahAdapter.clear();

          int sura = position + 1;
          int ayahCount = QuranInfo.getNumAyahs(sura);
          for (int i = 1; i <= ayahCount; i++) {
            ayahAdapter.add(QuranUtils.getLocalizedNumber(context, i));
          }

          int page = QuranInfo.getPageFromSuraAyah(sura, ayahSpinner.getSelectedItemPosition() + 1);
          input.setHint(QuranUtils.getLocalizedNumber(context, page));
          input.setText(null);
          suraSpinner.setTag(sura);
        }
      }

      @Override
      public void onNothingSelected(AdapterViewCompat<?> arg0) {
      }
    });

    ayahSpinner.setOnItemSelectedListener(new AdapterViewCompat.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterViewCompat<?> parent, View view, int position, long rowId) {
        if (ayahSpinner.getTag() == null) {
          // this is the initialization
          ayahSpinner.setTag(0);
        } else {
          // user interaction
          Context context = view.getContext();
          int ayah = position + 1;
          int page = QuranInfo.getPageFromSuraAyah(suraSpinner.getSelectedItemPosition() + 1, ayah);
          input.setHint(QuranUtils.getLocalizedNumber(context, page));
          input.setText(null);
          ayahSpinner.setTag(ayah);
        }
      }

      @Override
      public void onNothingSelected(AdapterViewCompat<?> arg0) {
      }
    });

    builder.setView(layout);
    builder.setNeutralButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        try {
          dialog.dismiss();
          String text = input.getText().toString();
          if (TextUtils.isEmpty(text)) {
            text = input.getHint().toString();
            int page = Integer.parseInt(text);
            int selectedSura = (int) suraSpinner.getTag();
            int selectedAyah = (int) ayahSpinner.getTag();

            Activity activity = getActivity();
            if (activity instanceof QuranActivity) {
              ((QuranActivity) activity).jumpToAndHighlight(page, selectedSura, selectedAyah);
            } else if (activity instanceof PagerActivity) {
              ((PagerActivity) activity).jumpToAndHighlight(page, selectedSura, selectedAyah);
            }
          } else {
            goToPage(text);
          }
        } catch (Exception e) {
          Log.d(TAG, "Could not jump, something went wrong...", e);
        }
      }
    });

    return builder.create();
  }

  private void goToPage(String text) {
    int page;
    try {
      page = Integer.parseInt(text);
    } catch (NumberFormatException nfe) {
      // this can happen if we are coming from IME_ACTION_GO
      return;
    }
    // user has interacted with 'Go to page' field, so we
    // need to verify if the input number is within
    // the acceptable range
    if (page < Constants.PAGES_FIRST || page > Constants.PAGES_LAST) {
      // maybe show a toast message?
      return;
    }

    Activity activity = getActivity();
    if (activity instanceof QuranActivity) {
      ((QuranActivity) activity).jumpTo(page);
    } else if (activity instanceof PagerActivity) {
      ((PagerActivity) activity).jumpTo(page);
    }
  }
}
