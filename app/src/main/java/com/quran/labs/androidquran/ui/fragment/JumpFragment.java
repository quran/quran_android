package com.quran.labs.androidquran.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.JumpDestination;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.ForceCompleteTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import timber.log.Timber;

public class JumpFragment extends DialogFragment {
  public static final String TAG = "JumpFragment";
  
  @Inject QuranInfo quranInfo;

  public JumpFragment() {
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Activity activity = getActivity();
    LayoutInflater inflater = activity.getLayoutInflater();
    @SuppressLint("InflateParams") View layout = inflater.inflate(R.layout.jump_dialog, null);

    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle(activity.getString(R.string.menu_jump));

    // Sura chooser
    final ForceCompleteTextView suraInput = layout.findViewById(
        R.id.sura_spinner);
    final String[] suras = activity.getResources().getStringArray(R.array.sura_names);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < suras.length; i++) {
      sb.append(QuranUtils.getLocalizedNumber(activity, (i + 1)));
      sb.append(". ");
      sb.append(suras[i]);
      suras[i] = sb.toString();
      sb.setLength(0);
    }
    InfixFilterArrayAdapter suraAdapter = new InfixFilterArrayAdapter(activity,
        android.R.layout.simple_spinner_dropdown_item, suras);
    suraInput.setAdapter(suraAdapter);

    // Ayah chooser
    final EditText ayahInput = layout.findViewById(R.id.ayah_spinner);

    // Page chooser
    final EditText pageInput = layout.findViewById(R.id.page_number);
    pageInput.setOnEditorActionListener((v, actionId, event) -> {
      boolean handled = false;
      if (actionId == EditorInfo.IME_ACTION_GO) {
        dismiss();
        goToPage(pageInput.getText().toString());
        handled = true;
      }
      return handled;
    });

    suraInput.setOnForceCompleteListener((v, position, rowId) -> {
      List<String> suraList = Arrays.asList(suras);
      String enteredText = suraInput.getText().toString();

      String suraName;
      if (position >= 0) { // user selects
        suraName = suraAdapter.getItem(position);
      } else if (suraList.contains(enteredText)) {
        suraName = enteredText;
      } else if (suraAdapter.isEmpty()) {
        suraName = null; // leave to the next code
      } else { // maybe first initialization or invalid input
        suraName = suraAdapter.getItem(0);
      }
      int sura = suraList.indexOf(suraName) + 1;
      if (sura == 0)
        sura = 1; // default to al-Fatiha

      suraInput.setTag(sura);
      suraInput.setText(suras[sura - 1]);
      //  trigger ayah change
      CharSequence ayahValue = ayahInput.getText();
      // space is intentional, to differentiate with value set by the user (delete/backspace)
      ayahInput.setText(ayahValue.length() > 0 ? ayahValue : " ");
    });

    ayahInput.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        Context context = getActivity();
        String ayahString = s.toString();
        int ayah = parseInt(ayahString, 1);

        Object suraTag = suraInput.getTag();
        if (suraTag != null) {
          int sura = (int) suraTag;
          int ayahCount = quranInfo.getNumAyahs(sura);
          ayah = Math.max(1, Math.min(ayahCount, ayah)); // ensure in 1..ayahCount
          int page = quranInfo.getPageFromSuraAyah(sura, ayah);
          pageInput.setHint(QuranUtils.getLocalizedNumber(context, page));
          pageInput.setText(null);
        }

        ayahInput.setTag(ayah);
        // seems numeric IM always use western arabic (not localized)
        String correctText = String.valueOf(ayah);
        // empty input means the user clears the input, we don't force to fill it, let him type
        if (s.length() > 0 && !correctText.equals(ayahString)) {
          s.replace(0, s.length(), correctText);
        }
      }
    });

    builder.setView(layout);
    builder.setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
      try {
        dismiss();
        String pageStr = pageInput.getText().toString();
        if (TextUtils.isEmpty(pageStr)) {
          pageStr = pageInput.getHint().toString();
          int page = Integer.parseInt(pageStr);
          int selectedSura = (int) suraInput.getTag();
          int selectedAyah = (int) ayahInput.getTag();

          if (activity instanceof JumpDestination) {
            ((JumpDestination) activity).jumpToAndHighlight(page, selectedSura, selectedAyah);
          }
        } else {
          goToPage(pageStr);
        }
      } catch (Exception e) {
        Timber.d(e, "Could not jump, something went wrong...");
      }
    });

    return builder.create();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    getDialog().getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
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
    if (page < Constants.PAGES_FIRST || page > quranInfo.getNumberOfPages()) {
      // maybe show a toast message?
      return;
    }

    Activity activity = getActivity();
    if (activity instanceof JumpDestination) {
      ((JumpDestination) activity).jumpTo(page);
    }
  }

  static int parseInt(String s, int defaultValue) {
    // May be extracted to util
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * ListAdapter that supports filtering by using case-insensitive infix (substring).
   */
  private static class InfixFilterArrayAdapter extends BaseAdapter implements Filterable {
    // May be extracted to other package

    private List<String> originalItems;
    private List<String> items;
    private LayoutInflater inflater;
    private int itemLayoutRes;
    private Filter filter = new ItemFilter();
    private final Object lock = new Object();

    InfixFilterArrayAdapter(@NonNull Context context, @LayoutRes int itemLayoutRes,
                            @NonNull String[] items) {
      this.items = originalItems = Arrays.asList(items);
      this.inflater = LayoutInflater.from(context);
      this.itemLayoutRes = itemLayoutRes;
    }

    @Override
    public int getCount() {
      return items.size();
    }

    @Override
    public String getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = inflater.inflate(itemLayoutRes, parent, false);
      }
      // As no fieldId is known/assigned, assume it is a TextView
      TextView text = (TextView) convertView;
      text.setText(getItem(position));
      return convertView;
    }

    @Override
    public Filter getFilter() {
      return filter;
    }

    /**
     * Filter that do filtering by matching case-insensitive infix of the input.
     */
    private class ItemFilter extends Filter {

      @Override
      protected FilterResults performFiltering(CharSequence constraint) {
        final FilterResults results = new FilterResults();

        // The items never change after construction, not sure if really needs to copy
        final ArrayList<String> copy;
        synchronized (lock) {
          copy = new ArrayList<>(originalItems);
        }

        if (constraint == null || constraint.length() == 0) {
          results.values = copy;
          results.count = copy.size();
        } else {
          final String infix = constraint.toString().toLowerCase();
          final ArrayList<String> filteredCopy = new ArrayList<>();
          for (String i : copy) {
            if (i == null)
              continue;
            String value = i.toLowerCase();
            if (value.contains(infix)) {
              filteredCopy.add(i);
            }
          }

          results.values = filteredCopy;
          results.count = filteredCopy.size();
        }
        return results;
      }

      @Override
      protected void publishResults(CharSequence constraint, FilterResults results) {
        //noinspection unchecked
        items = (List<String>) results.values;
        if (results.count > 0) {
          notifyDataSetChanged();
        } else {
          notifyDataSetInvalidated();
        }
      }
    }

  }

}
