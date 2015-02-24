package com.quran.labs.androidquran.task;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class ShareAyahTask extends PagerActivityTask<Void, Void, List<QuranAyah>> {
  private SuraAyah start, end;
  private boolean copy;

  public ShareAyahTask(PagerActivity activity, SuraAyah start, SuraAyah end, boolean copy) {
    super(activity);
    this.start = start;
    this.end = end;
    this.copy = copy;
  }

  @Override
  protected List<QuranAyah> doInBackground(Void... params) {
    if (start == null || end == null) return null;
    List<QuranAyah> verses = new ArrayList<QuranAyah>();
    try {
      DatabaseHandler ayahHandler =
          new DatabaseHandler(getActivity(),
              QuranDataProvider.QURAN_ARABIC_DATABASE);
      Cursor cursor = ayahHandler.getVerses(start.sura, start.ayah,
          end.sura, end.ayah, DatabaseHandler.ARABIC_TEXT_TABLE);
      while (cursor.moveToNext()) {
        QuranAyah verse = new QuranAyah(cursor.getInt(0), cursor.getInt(1));
        verse.setText(cursor.getString(2));
        verses.add(verse);
      }
      cursor.close();
      ayahHandler.closeDatabase();
    }
    catch (Exception e){
    }

    return verses;
  }

  @Override
  protected void onPostExecute(List<QuranAyah> verses) {
    super.onPostExecute(verses);
    if (verses == null || verses.isEmpty()) {
      return;
    }
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }
    // TODO what's the best text format for multiple ayahs?
    StringBuilder sb = new StringBuilder();
    final int size = verses.size();
    final QuranAyah firstAayah = verses.get(0);
    final QuranAyah lastAayah = verses.get(size - 1);
    // append ( before first ayah
    sb.append("(").append(firstAayah.getText());

    switch (size) {
      case 1:
        // nothing to do here, just
        break;
      case 2:
        sb.append(" * ");
        sb.append(lastAayah.getText());
        break;
      default:
        sb.append(" * ");
        for (int count = 1; count < size - 1; count++) {
          sb.append(verses.get(count).getText());
          sb.append(" * ");
        }
        // and append the last ayah
        sb.append(lastAayah.getText());
    }

    // append ) and a new line after last ayah
    sb.append(")\n");
    // append [ before sura label
    sb.append("[");
    sb.append(QuranInfo.getSuraName(activity, firstAayah.getSura(), true));
    sb.append(" ");
    if (size > 1) {
      sb.append(firstAayah.getAyah()).append(" - ");
      if (firstAayah.getSura() != lastAayah.getSura()) {
        sb.append(QuranInfo.getSuraName(activity, lastAayah.getSura(), true));
        sb.append(" ");
      }
    }
    sb.append(lastAayah.getAyah());
    // close sura label and append two new lines
    sb.append("]\n\n");

    sb.append(activity.getString(R.string.via_string));
    String text = sb.toString();
    if (copy) {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
        clipTextApi11(activity, text);
      } else {
        android.text.ClipboardManager cm = (android.text.ClipboardManager)
            activity.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setText(text);
      }
      Toast.makeText(activity, activity.getString(R.string.ayah_copied_popup),
          Toast.LENGTH_SHORT).show();
    } else {
      final Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setType("text/plain");
      intent.putExtra(Intent.EXTRA_TEXT, text);
      activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_ayah)));
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void clipTextApi11(Activity activity, String text) {
    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(activity.getString(R.string.app_name), text);
    cm.setPrimaryClip(clip);
  }
}
