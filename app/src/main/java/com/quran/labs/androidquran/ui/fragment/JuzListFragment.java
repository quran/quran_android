package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.JuzView;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

import static com.quran.labs.androidquran.data.Constants.JUZ2_COUNT;

public class JuzListFragment extends Fragment {
  private static int[] sEntryTypes = {
      JuzView.TYPE_JUZ, JuzView.TYPE_QUARTER,
      JuzView.TYPE_HALF, JuzView.TYPE_THREE_QUARTERS };

  private RecyclerView mRecyclerView;
  private Disposable disposable;

  public static JuzListFragment newInstance() {
    return new JuzListFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
      ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = getActivity();
    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
    mRecyclerView.setItemAnimator(new DefaultItemAnimator());

    final QuranListAdapter adapter =
        new QuranListAdapter(context, mRecyclerView, getJuz2List(), false);
    mRecyclerView.setAdapter(adapter);
    return view;
  }

  @Override
  public void onPause() {
    if (disposable != null) {
      disposable.dispose();
    }
    super.onPause();
  }

  @Override
  public void onResume() {
    final Activity activity = getActivity();

    if (activity instanceof QuranActivity) {
      disposable = ((QuranActivity) activity).getLatestPageObservable()
          .first(Constants.NO_PAGE)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeWith(new DisposableSingleObserver<Integer>() {
            @Override
            public void onSuccess(Integer recentPage) {
              if (recentPage != Constants.NO_PAGE) {
                int juz = QuranInfo.getJuzFromPage(recentPage);
                int position = (juz - 1) * 9;
                mRecyclerView.scrollToPosition(position);
              }
            }

            @Override
            public void onError(Throwable e) {
            }
          });
    }

    QuranSettings settings = QuranSettings.getInstance(activity);
    if (settings.isArabicNames()) {
      updateScrollBarPositionHoneycomb();
    }

    super.onResume();
  }

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
        final String juzTitle = activity.getString(R.string.juz2_description,
            QuranUtils.getLocalizedNumber(activity, juz));
        final QuranRow.Builder builder = new QuranRow.Builder()
            .withType(QuranRow.HEADER)
            .withText(juzTitle)
            .withPage(QuranInfo.JUZ_PAGE_START[juz - 1]);
        elements[ctr++] = builder.build();
      }

      final String verseString = getString(R.string.quran_ayah, pos[1]);
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
