package com.quran.labs.androidquran.ui.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.StorageUtils;

import java.util.List;

import androidx.preference.ListPreference;

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
public class DataListPreference extends ListPreference {
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
      mDescriptions[i] = storage.getMountPoint() + " " +
          context.getString(R.string.prefs_megabytes_int, storage.getFreeSpace());
      displayNames[i] = storage.getLabel() + "\n" + mDescriptions[i];
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
}
