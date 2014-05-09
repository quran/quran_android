package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.PagerActivity;

public class AyahAudioFragment extends AyahActionFragment implements View.OnClickListener {

  private ImageButton mPlayAudio;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Context context = getActivity();
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(LinearLayout.VERTICAL);
    mPlayAudio = new ImageButton(context);
    mPlayAudio.setImageResource(R.drawable.ic_play);
    mPlayAudio.setId(R.id.cab_play_from_here);
    view.addView(mPlayAudio);
    mPlayAudio.setOnClickListener(this);
    return view;
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.cab_play_from_here:
        Activity activity = getActivity();
        if (activity instanceof PagerActivity) {
          ((PagerActivity)activity).playFromAyah(mStart.getPage(), mStart.sura, mStart.ayah);
        }
        break;
      default:
        break;
    }
  }

}
