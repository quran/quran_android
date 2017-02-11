package com.quran.labs.androidquran.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.ForceCompleteTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

public class JumpFragment extends DialogFragment {
  public static final String TAG = "JumpFragment";

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

    // Sura Spinner
    final ForceCompleteTextView suraSpinner = (ForceCompleteTextView) layout.findViewById(
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
    InfixFilterArrayAdapter adapter = new InfixFilterArrayAdapter(activity,
        android.R.layout.simple_spinner_dropdown_item, suras);
    suraSpinner.setAdapter(adapter);

    // Ayah Spinner
    final EditText ayahSpinner = (EditText) layout.findViewById(R.id.ayah_spinner);

    // Page text
    final EditText input = (EditText) layout.findViewById(R.id.page_number);
    input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        boolean handled = false;
        if (actionId == EditorInfo.IME_ACTION_GO) {
          dismiss();
          goToPage(input.getText().toString());
          handled = true;
        }
        return handled;
      }
    });

    input.setHint(QuranUtils.getLocalizedNumber(activity, 1));
    suraSpinner.setTag(1); // al-Fatiha
    suraSpinner.setText(suras[0]);
    suraSpinner.setOnItemClickListener((AdapterView<?> parent, View view, int position,
                                        long rowId) -> {
      Context context = getActivity();

      String suraName = (String) parent.getItemAtPosition(position);
      int sura = Arrays.asList(suras).indexOf(suraName) + 1;
      if (sura == 0) // default to al-Fatiha
        sura = 1;
      int ayahCount = QuranInfo.getNumAyahs(sura);

      int ayah = parseInt(ayahSpinner.getText().toString(), 1);
      int allowedAyah = Math.max(1, Math.min(ayahCount, ayah)); // ensure 1..ayahCount
      if (ayah != allowedAyah) {
        ayah = allowedAyah;
        // seems numeric IM always use latin
        ayahSpinner.setText(String.valueOf(ayah)); // (QuranUtils.getLocalizedNumber(context, ayah));
      }

      int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      input.setHint(QuranUtils.getLocalizedNumber(context, page));
      input.setText(null);
      suraSpinner.setTag(sura);
    });

    ayahSpinner.setTag(1);
    // seems numeric IM always use latin
    ayahSpinner.setText(String.valueOf(1)); // (QuranUtils.getLocalizedNumber(activity, 1));
    ayahSpinner.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable s) {
        Context context = getActivity();
        int sura = (int) suraSpinner.getTag();
        int ayahCount = QuranInfo.getNumAyahs(sura);
        int ayah = parseInt(s.toString(), 1);
        int allowedAyah = Math.max(1, Math.min(ayahCount, ayah));
        if (ayah != allowedAyah) {
          ayah = allowedAyah;
          s.clear();
          s.append(QuranUtils.getLocalizedNumber(context, ayah));
        }
        int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
        input.setHint(QuranUtils.getLocalizedNumber(context, page));
        input.setText(null);
        ayahSpinner.setTag(ayah);
      }
    });

    builder.setView(layout);
    builder.setPositiveButton(getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        try {
          dismiss();
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
          Timber.d(e, "Could not jump, something went wrong...");
        }
      }
    });

    return builder.create();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
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

  static int parseInt(String s, int defaultValue) {
    // May be extracted to util
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * ListAdapter that supports filtering by using case-insensitive infix (substring)
   */
  private static class InfixFilterArrayAdapter extends BaseAdapter implements Filterable {
    // May be extracted to other package

    private List<String> originalItems;
    private List<String> items;
    private LayoutInflater inflater;
    private int itemLayoutRes;
    private Filter filter = new ItemFilter();
    private final Object lock = new Object();

    public InfixFilterArrayAdapter(@NonNull Context context, @LayoutRes int itemLayoutRes,
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
     * Filter that do filtering by matching case-insensitive infix of the input
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
