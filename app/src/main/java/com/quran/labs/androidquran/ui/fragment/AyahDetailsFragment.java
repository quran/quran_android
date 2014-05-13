package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;
import com.quran.labs.androidquran.util.QuranAppUtils;

import java.util.ArrayList;
import java.util.List;

public class AyahDetailsFragment extends AyahActionFragment implements View.OnClickListener {

  private ProgressDialog mProgressDialog;

  private TextView mAyahDetails;
  private ImageButton mBookmarkAyah, mShareAyahLink, mShareAyahText, mCopyAyah;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.ayah_details_fragment, container, false);
    mAyahDetails = (TextView) view.findViewById(R.id.ayah_details);
    mBookmarkAyah = (ImageButton) view.findViewById(R.id.bookmark_ayah);
    mShareAyahLink = (ImageButton) view.findViewById(R.id.share_ayah_link);
    mShareAyahText = (ImageButton) view.findViewById(R.id.share_ayah_text);
    mCopyAyah = (ImageButton) view.findViewById(R.id.copy_ayah);

    mAyahDetails.setOnClickListener(this);
    mBookmarkAyah.setOnClickListener(this);
    mShareAyahLink.setOnClickListener(this);
    mShareAyahText.setOnClickListener(this);
    mCopyAyah.setOnClickListener(this);

    return view;
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    if (mProgressDialog != null){
      mProgressDialog.hide();
      mProgressDialog = null;
    }
  }

  @Override
  public void refreshView() {
    super.refreshView();
    if (mStart == null || mEnd == null) return;
    new RefreshBookmarkIconTask(mStart).execute();
    mAyahDetails.setText(mStart.toString() + " - " + mEnd.toString());
  }

  public void updateAyahBookmarkIcon(SuraAyah suraAyah, boolean bookmarked) {
    if (mStart.equals(suraAyah)) {
      mBookmarkAyah.setImageResource(bookmarked ? R.drawable.favorite : R.drawable.not_favorite);
    }
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.bookmark_ayah:
        Activity activity = getActivity();
        if (activity instanceof PagerActivity) {
          ((PagerActivity)activity).toggleBookmark(mStart.sura, mStart.ayah, mStart.getPage());
        }
        break;
      case R.id.share_ayah_link:
        mCurrentTask = new ShareQuranApp(mStart, mEnd).execute();
        break;
      case R.id.share_ayah_text:
        mCurrentTask = new ShareAyahTask(mStart, mEnd, false).execute();
        break;
      case R.id.copy_ayah:
        mCurrentTask = new ShareAyahTask(mStart, mEnd, true).execute();
        break;
      default:
        break;
    }
  }

  private class RefreshBookmarkIconTask extends AsyncTask<Void, Void, Boolean> {
    private SuraAyah mSuraAyah;

    public RefreshBookmarkIconTask(SuraAyah suraAyah) {
      mSuraAyah = suraAyah;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      if (mSuraAyah == null) return null;
      BookmarksDBAdapter adapter = null;
      Activity activity = getActivity();
      if (activity != null && activity instanceof BookmarkHandler){
        adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
      }

      if (adapter == null){ return null; }

      boolean bookmarked = adapter.getBookmarkId(
          mSuraAyah.sura, mSuraAyah.ayah, mSuraAyah.getPage()) >= 0;
      return bookmarked;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (result != null){
        updateAyahBookmarkIcon(mSuraAyah, result);
      }
    }

  }

  private class ShareQuranApp extends AsyncTask<Void, Void, String> {
    private SuraAyah start;
    private SuraAyah end;
    private String mKey;

    public ShareQuranApp(SuraAyah start, SuraAyah end) {
      this.start = start;
      this.end = end;
    }

    @Override
    protected void onPreExecute() {
      Activity activity = getActivity();
      if (activity != null){
        mKey = activity.getString(R.string.quranapp_key);
        mProgressDialog = new ProgressDialog(activity);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setMessage(
            activity.getString(R.string.index_loading));
        mProgressDialog.show();
      }
    }

    @Override
    protected String doInBackground(Void... params){
      if (start == null || end == null) return null;
      int sura = start.sura;
      int startAyah = start.ayah;
      int endAyah = end.sura == start.sura ? end.ayah : QuranInfo.getNumAyahs(start.sura);
      // TODO support spanning multiple suras
      String url = QuranAppUtils.getQuranAppUrl(mKey, sura, startAyah, endAyah);
      return url;
    }

    @Override
    protected void onPostExecute(String url) {
      if (mProgressDialog != null && mProgressDialog.isShowing()){
        mProgressDialog.dismiss();
        mProgressDialog = null;
      }

      Activity activity = getActivity();
      if (activity != null && !TextUtils.isEmpty(url)){
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        activity.startActivity(Intent.createChooser(intent,
            activity.getString(R.string.share_ayah)));
      }

      mCurrentTask = null;
    }
  }

  private class ShareAyahTask extends AsyncTask<Void, Void, List<QuranAyah>> {
    private SuraAyah start, end;
    private boolean copy;

    public ShareAyahTask(SuraAyah start, SuraAyah end, boolean copy) {
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
      mCurrentTask = null;
    }
  }

}
