package com.quran.labs.androidquran.task;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.text.ClipboardManager;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;

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
    Activity activity = getActivity();
    if (verses != null && !verses.isEmpty() && activity != null) {
      StringBuilder sb = new StringBuilder();
      // TODO what's the best text format for multiple ayahs
      for (QuranAyah verse : verses) {
        sb.append("(").append(verse.getText()).append(") [");
        sb.append(QuranInfo.getSuraName(activity, verse.getSura(), true));
        sb.append(" : ").append(verse.getAyah()).append("]").append("\n\n");
      }
      sb.append(activity.getString(R.string.via_string));
      String text = sb.toString();
      if (copy) {
        ClipboardManager cm = (ClipboardManager)activity.
            getSystemService(Activity.CLIPBOARD_SERVICE);
        if (cm != null){
          cm.setText(text);
          Toast.makeText(activity, activity.getString(
                  R.string.ayah_copied_popup),
              Toast.LENGTH_SHORT
          ).show();
        }
      } else {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(intent,
            activity.getString(R.string.share_ayah)));
      }
    }
  }
}
