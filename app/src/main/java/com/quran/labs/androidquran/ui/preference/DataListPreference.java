package com.quran.labs.androidquran.ui.preference;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.StorageUtils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Here we show storage title and free space amount (currently, in MB) in summary.
 * However, `ListPreference` does not provide summary text out of the box, and thus
 * we use a custom layout with two `TextView`s, for a title and a summary,
 * and a `CheckedTextView` for a radio-button.
 * We remove the `CheckedTextView`'s title during runtime and use one of the
 * `TextView`s instead to represent the title.
 *
 * Also, we extend from `QuranListPreference` in order not to duplicate code for
 * setting dialog title color.
 */
public class DataListPreference extends QuranListPreference {
  private CharSequence[] mDescriptions;

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public DataListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public DataListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public DataListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public DataListPreference(Context context) {
    super(context);
  }

  public void setLabelsAndSummaries(Context context, int appSize,
                                    List<StorageUtils.Storage> storageList) {
    String summary = context.getString(R.string.prefs_app_location_summary) + "\n"
        + context.getString(R.string.prefs_app_size) + " " +
        context.getString(R.string.prefs_megabytes_int, appSize);
    setSummary(summary);

    CharSequence[] values = new CharSequence[storageList.size()];
    CharSequence[] displayNames = new CharSequence[storageList.size()];
    mDescriptions = new CharSequence[storageList.size()];
    StorageUtils.Storage storage;
    for (int i = 0; i < storageList.size(); i++) {
      storage = storageList.get(i);
      values[i] = storage.getMountPoint();
      displayNames[i] = storage.getLabel();
      mDescriptions[i] = storage.getMountPoint() + " " +
          context.getString(R.string.prefs_megabytes_int, storage.getFreeSpace());
    }
    setEntries(displayNames);
    setEntryValues(values);
    final QuranSettings settings = QuranSettings.getInstance(context);
    String current = settings.getAppCustomLocation();
    if (TextUtils.isEmpty(current)) {
      current = values[0].toString();
    }
    setValue(current);
  }

  @Override
  protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder) {
    int selectedIndex = findIndexOfValue(getValue());
    ListAdapter adapter = new StorageArrayAdapter(getContext(), R.layout.data_storage_location_item,
        getEntries(), selectedIndex, mDescriptions);
    builder.setAdapter(adapter, this);
    super.onPrepareDialogBuilder(builder);
  }

  static class ViewHolder {
    public TextView titleTextView;
    public TextView summaryTextView;
    public CheckedTextView checkedTextView;
  }

  public class StorageArrayAdapter extends ArrayAdapter<CharSequence> {
    private int mSelectedIndex = 0;
    private CharSequence[] mFreeSpaces;

    public StorageArrayAdapter(Context context, int textViewResourceId, CharSequence[] objects,
                               int selectedIndex, CharSequence[] freeSpaces) {
      super(context, textViewResourceId, objects);
      mSelectedIndex = selectedIndex;
      mFreeSpaces = freeSpaces;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder;
      if (convertView == null) {
        LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
        convertView = inflater.inflate(R.layout.data_storage_location_item, parent, false);

        holder = new ViewHolder();
        holder.titleTextView = (TextView) convertView.findViewById(R.id.storage_label);
        holder.summaryTextView = (TextView) convertView.findViewById(R.id.available_free_space);
        holder.checkedTextView = (CheckedTextView) convertView.findViewById(R.id.checked_text_view);
        convertView.setTag(holder);
      }

      holder = (ViewHolder) convertView.getTag();
      holder.titleTextView.setText(getItem(position));
      holder.summaryTextView.setText(mFreeSpaces[position]);
      holder.checkedTextView.setText(null); // we have a 'custom' label
      if (position == mSelectedIndex) {
        holder.checkedTextView.setChecked(true);
      }

      return convertView;
    }
  }
}
