package com.quran.labs.androidquran.ui;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.SheikhInfo;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Subscription;
import rx.android.app.AppObservable;
import rx.functions.Action1;

public class AudioManagerActivity extends ActionBarActivity {
  private ProgressBar mProgressBar;
  private Subscription mSubscription;
  private ShuyookhAdapter mAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getSupportActionBar().setTitle(R.string.audio_manager);

    setContentView(R.layout.audio_manager);

    RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    String[] names = getResources().getStringArray(R.array.quran_readers_name);
    String[] paths = getResources().getStringArray(R.array.quran_readers_path);
    mAdapter = new ShuyookhAdapter(names, paths);
    recyclerView.setAdapter(mAdapter);

    mProgressBar = (ProgressBar) findViewById(R.id.progress);

    String basePath = AudioUtils.getAudioRootDirectory(this);
    mSubscription = AppObservable.bindActivity(this,
        AudioManagerUtils.shuyookhDownloadObservable(basePath, paths))
        .subscribe(mOnDownloadInfo);
  }

  @Override
  protected void onDestroy() {
    mSubscription.unsubscribe();
    super.onDestroy();
  }

  private Action1<List<SheikhInfo>> mOnDownloadInfo =
      new Action1<List<SheikhInfo>>() {
        @Override
        public void call(List<SheikhInfo> downloadInfo) {
          mProgressBar.setVisibility(View.GONE);
          mAdapter.setDownloadInfo(downloadInfo);
          mAdapter.notifyDataSetChanged();
        }
      };

  private class ShuyookhAdapter extends RecyclerView.Adapter<SheikhViewHolder> {
    private final LayoutInflater mInflater;
    private final String[] mShuyookhNames;
    private final String[] mShuyookhPaths;
    private final Map<String, SheikhInfo> mDownloadInfoMap;

    public ShuyookhAdapter(String[] shuyookhNames, String[] shuyookhPaths) {
      mShuyookhNames = shuyookhNames;
      mShuyookhPaths = shuyookhPaths;
      mDownloadInfoMap = new HashMap<>();
      mInflater = LayoutInflater.from(AudioManagerActivity.this);
    }

    public void setDownloadInfo(List<SheikhInfo> downloadInfo) {
      for (SheikhInfo info : downloadInfo) {
        mDownloadInfoMap.put(info.path, info);
      }
    }

    @Override
    public SheikhViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new SheikhViewHolder(mInflater.inflate(R.layout.audio_manager_row, parent, false));
    }

    @Override
    public void onBindViewHolder(SheikhViewHolder holder, int position) {
      holder.name.setText(mShuyookhNames[position]);

      SheikhInfo info = mDownloadInfoMap.get(mShuyookhPaths[position]);
      holder.quantity.setText("Downloaded " +
          info.downloadedSuras.size() + " suras and " +
          info.partialSuras.size() + " partials.");
    }

    @Override
    public int getItemCount() {
      return mDownloadInfoMap.size() == 0 ? 0 : mShuyookhNames.length;
    }
  }

  private static class SheikhViewHolder extends RecyclerView.ViewHolder {
    public final TextView name;
    public final TextView quantity;

    public SheikhViewHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.name);
      quantity = (TextView) itemView.findViewById(R.id.quantity);
    }
  }
}
