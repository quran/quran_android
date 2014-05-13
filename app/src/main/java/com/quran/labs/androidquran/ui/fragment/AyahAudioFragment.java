package com.quran.labs.androidquran.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.PagerActivity;

public class AyahAudioFragment extends AyahActionFragment implements View.OnClickListener {

  private ImageButton mPlayAudio;
  private ImageButton mStopAudio;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.ayah_audio_fragment, container, false);
    mPlayAudio = (ImageButton) view.findViewById(R.id.play_ayah);
    mStopAudio = (ImageButton) view.findViewById(R.id.stop_ayah);

    mPlayAudio.setOnClickListener(this);
    mStopAudio.setOnClickListener(this);

    return view;
  }

  @Override
  public void onClick(View v) {
    PagerActivity activity = (PagerActivity) getActivity();
    switch (v.getId()) {
      case R.id.play_ayah:
        activity.playFromAyah(mStart.getPage(), mStart.sura, mStart.ayah);
        break;
      case R.id.stop_ayah:
        activity.onStopPressed();
        break;
      default:
        break;
    }
  }

}
