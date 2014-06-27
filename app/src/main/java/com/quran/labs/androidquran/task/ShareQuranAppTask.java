package com.quran.labs.androidquran.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.text.TextUtils;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.QuranAppUtils;

public class ShareQuranAppTask extends PagerActivityTask<Void, Void, String> {
  private SuraAyah start;
  private SuraAyah end;
  private String mKey;

  private ProgressDialog mProgressDialog;

  public ShareQuranAppTask(PagerActivity activity, SuraAyah start, SuraAyah end) {
    super(activity);
    this.start = start;
    this.end = end;
  }

  @Override
  protected void onPreExecute() {
    super.onPreExecute();
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
    int endAyah = end.sura == start.sura ? end.ayah :
            QuranInfo.getNumAyahs(start.sura);
    // quranapp only supports sharing within a sura
    return QuranAppUtils.getQuranAppUrl(mKey, sura, startAyah, endAyah);
  }

  @Override
  protected void onPostExecute(String url) {
    super.onPostExecute(url);
    dismissProgressDialog();

    Activity activity = getActivity();
    if (activity != null && !TextUtils.isEmpty(url)){
      Intent intent = new Intent(Intent.ACTION_SEND);
      intent.setType("text/plain");
      intent.putExtra(Intent.EXTRA_TEXT, url);
      activity.startActivity(Intent.createChooser(intent,
          activity.getString(R.string.share_ayah)));
    }
  }

  @Override
  protected void onCancelled() {
    super.onCancelled();
    dismissProgressDialog();
  }

  private void dismissProgressDialog() {
    if (mProgressDialog != null && mProgressDialog.isShowing()){
      mProgressDialog.dismiss();
      mProgressDialog = null;
    }
  }

}
