package com.quran.labs.androidquran.ui;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.SheikhInfo;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
    final ActionBar ab = getSupportActionBar();
    ab.setTitle(R.string.audio_manager);
    ab.setDisplayHomeAsUpEnabled(true);

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

  private View.OnClickListener mOnClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
    }
  };

  private View.OnClickListener mOnImageClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
    }
  };

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

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
      int fullyDownloaded = info.downloadedSuras.size();
      holder.quantity.setText(
          getResources().getQuantityString(R.plurals.files_downloaded,
            fullyDownloaded, fullyDownloaded));
    }

    @Override
    public int getItemCount() {
      return mDownloadInfoMap.size() == 0 ? 0 : mShuyookhNames.length;
    }
  }

  private class SheikhViewHolder extends RecyclerView.ViewHolder {
    public final TextView name;
    public final TextView quantity;
    public final ImageView image;

    public SheikhViewHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.name);
      quantity = (TextView) itemView.findViewById(R.id.quantity);

      image = (ImageView) itemView.findViewById(R.id.image);
      image.setOnClickListener(mOnImageClickListener);
      itemView.setOnClickListener(mOnClickListener);
    }
  }
}
