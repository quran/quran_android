package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
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
import static com.quran.labs.androidquran.data.Constants.PAGES_LAST;
import static com.quran.labs.androidquran.data.Constants.SURAS_COUNT;

public class SuraListFragment extends Fragment {

  private RecyclerView mRecyclerView;

  public static SuraListFragment newInstance() {
    return new SuraListFragment();
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
      ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.quran_list, container, false);

    final Context context = getActivity();
    mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(context));
    mRecyclerView.setItemAnimator(new DefaultItemAnimator());

    final QuranListAdapter adapter =
        new QuranListAdapter(context, mRecyclerView, getSuraList(), true);
    mRecyclerView.setAdapter(adapter);
    return view;
  }

  @Override
  public void onResume() {
    final Activity activity = getActivity();
    ((QuranApplication) activity.getApplication()).refreshLocale(false);

    int lastPage = QuranSettings.getLastPage(activity);
    if (lastPage != Constants.NO_PAGE_SAVED &&
        lastPage >= Constants.PAGES_FIRST &&
        lastPage <= Constants.PAGES_LAST) {
      int sura = QuranInfo.PAGE_SURA_START[lastPage - 1];
      int juz = QuranInfo.getJuzFromPage(lastPage);
      int position = sura + juz - 1;
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

  private QuranRow[] getSuraList() {
    int next;
    int pos = 0;
    int sura = 1;
    QuranRow[] elements = new QuranRow[SURAS_COUNT + JUZ2_COUNT];

    Activity activity = getActivity();
    for (int juz = 1; juz <= JUZ2_COUNT; juz++) {
      final String headerTitle = QuranInfo.getJuzTitle(activity) + " " +
          QuranUtils.getLocalizedNumber(activity, juz);
      final QuranRow.Builder headerBuilder = new QuranRow.Builder()
          .withType(QuranRow.HEADER)
          .withText(headerTitle)
          .withPage(QuranInfo.JUZ_PAGE_START[juz - 1]);
      elements[pos++] = headerBuilder.build();
      next = (juz == JUZ2_COUNT) ? PAGES_LAST + 1 :
          QuranInfo.JUZ_PAGE_START[juz];

      while ((sura <= SURAS_COUNT) &&
          (QuranInfo.SURA_PAGE_START[sura - 1] < next)) {
        String title = QuranInfo.getSuraName(activity, sura,
            activity.getResources().getBoolean(R.bool.show_surat_prefix));
        final QuranRow.Builder builder = new QuranRow.Builder()
            .withText(title)
            .withMetadata(QuranInfo.getSuraListMetaString(activity, sura))
            .withSura(sura)
            .withPage(QuranInfo.SURA_PAGE_START[sura - 1]);
        elements[pos++] = builder.build();
        sura++;
      }
    }

    return elements;
  }
}
