package com.quran.labs.androidquran.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.task.RefreshBookmarkIconTask;
import com.quran.labs.androidquran.task.ShareAyahTask;
import com.quran.labs.androidquran.task.ShareQuranAppTask;
import com.quran.labs.androidquran.ui.PagerActivity;

public class AyahDetailsFragment extends AyahActionFragment implements View.OnClickListener {

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
  public void refreshView() {
    super.refreshView();
    if (mStart == null || mEnd == null) return;
    new RefreshBookmarkIconTask((PagerActivity) getActivity(), mStart).execute();
    mAyahDetails.setText(mStart.toString() + " - " + mEnd.toString());
  }

  public void updateAyahBookmarkIcon(SuraAyah suraAyah, boolean bookmarked) {
    if (mStart.equals(suraAyah)) {
      mBookmarkAyah.setImageResource(bookmarked ? R.drawable.favorite : R.drawable.not_favorite);
    }
  }

  @Override
  public void onClick(View v) {
    PagerActivity activity = (PagerActivity) getActivity();
    switch (v.getId()) {
      case R.id.bookmark_ayah:
        activity.toggleBookmark(mStart.sura, mStart.ayah, mStart.getPage());
        break;
      case R.id.share_ayah_link:
        new ShareQuranAppTask(activity, mStart, mEnd).execute();
        break;
      case R.id.share_ayah_text:
        new ShareAyahTask(activity, mStart, mEnd, false).execute();
        break;
      case R.id.copy_ayah:
        new ShareAyahTask(activity, mStart, mEnd, true).execute();
        break;
      default:
        break;
    }
  }

}
