package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.ui.util.QuranListTouchListener;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.JuzView;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import static com.quran.labs.androidquran.data.Constants.JUZ2_COUNT;

public class JuzListFragment extends Fragment {
  private static int[] sEntryTypes = {
      JuzView.TYPE_JUZ, JuzView.TYPE_QUARTER,
      JuzView.TYPE_HALF, JuzView.TYPE_THREE_QUARTERS };

  private RecyclerView mRecyclerView;

  public static JuzListFragment newInstance() {
    return new JuzListFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
      ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = getActivity();
    final QuranListAdapter adapter =
        new QuranListAdapter(context, getJuz2List(), true);
    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
    mRecyclerView.setItemAnimator(new DefaultItemAnimator());
    mRecyclerView.setAdapter(adapter);
    mRecyclerView.addOnItemTouchListener(
        new QuranListTouchListener(context, mRecyclerView));
    return view;
  }

  @Override
  public void onResume() {
    final Activity activity = getActivity();

    int lastPage = QuranSettings.getLastPage(activity);
    if (lastPage != Constants.NO_PAGE_SAVED) {
      int juz = QuranInfo.getJuzFromPage(lastPage);
      int position = (juz - 1) * 9;
      mRecyclerView.scrollToPosition(position);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
        QuranSettings.isArabicNames(activity)) {
      updateScrollBarPositionHoneycomb();
    }

    super.onResume();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void updateScrollBarPositionHoneycomb() {
    mRecyclerView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
  }

  private QuranRow[] getJuz2List() {
    Activity activity = getActivity();
    Resources res = getResources();
    String[] quarters = res.getStringArray(R.array.quarter_prefix_array);
    QuranRow[] elements = new QuranRow[JUZ2_COUNT * (8 + 1)];

    int ctr = 0;
    for (int i = 0; i < (8 * JUZ2_COUNT); i++) {
      int[] pos = QuranInfo.QUARTERS[i];
      int page = QuranInfo.getPageFromSuraAyah(pos[0], pos[1]);

      if (i % 8 == 0) {
        int juz = 1 + (i / 8);
        final String juzTitle = QuranInfo.getJuzTitle(activity) +
            " " + QuranUtils.getLocalizedNumber(activity, juz);
        final QuranRow.Builder builder = new QuranRow.Builder()
            .withType(QuranRow.HEADER)
            .withText(juzTitle)
            .withPage(QuranInfo.JUZ_PAGE_START[juz - 1]);
        elements[ctr++] = builder.build();
      }

      final String verseString = getString(R.string.quran_ayah) + " " + pos[1];
      final String metadata =
          QuranInfo.getSuraName(activity, pos[0], true) +  ", " + verseString;
      final QuranRow.Builder builder = new QuranRow.Builder()
          .withText(quarters[i])
          .withMetadata(metadata)
          .withPage(page)
          .withJuzType(sEntryTypes[i % 4]);
      if (i % 4 == 0) {
        final String overlayText =
            QuranUtils.getLocalizedNumber(activity, 1 + (i / 4));
        builder.withJuzOverlayText(overlayText);
      }
      elements[ctr++] = builder.build();
    }

    return elements;
  }
}
