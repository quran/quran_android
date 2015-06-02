package com.quran.labs.androidquran.ui;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.SheikhInfo;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
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

public class AudioManagerActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener {
  private static final String AUDIO_DOWNLOAD_KEY = "AudioManager.DownloadKey";

  private ProgressBar mProgressBar;
  private Subscription mSubscription;
  private ShuyookhAdapter mAdapter;
  private RecyclerView mRecyclerView;
  private DefaultDownloadReceiver mReceiver;
  private String mBasePath;
  private String[] mShuyookhPaths;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.audio_manager);
      ab.setDisplayHomeAsUpEnabled(true);
    }

    setContentView(R.layout.audio_manager);

    mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    mRecyclerView.setItemAnimator(new DefaultItemAnimator());

    String[] names = getResources().getStringArray(R.array.quran_readers_name);
    mShuyookhPaths = getResources().getStringArray(R.array.quran_readers_path);
    mAdapter = new ShuyookhAdapter(names, mShuyookhPaths);
    mRecyclerView.setAdapter(mAdapter);

    mProgressBar = (ProgressBar) findViewById(R.id.progress);

    mBasePath = QuranFileUtils.getQuranAudioDirectory(this);
    getShuyookhData();
  }

  private void getShuyookhData() {
    if (mSubscription != null) {
      mSubscription.unsubscribe();
    }
    mSubscription = AppObservable.bindActivity(this,
        AudioManagerUtils.shuyookhDownloadObservable(mBasePath, mShuyookhPaths))
        .subscribe(mOnDownloadInfo);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mReceiver = new DefaultDownloadReceiver(this,
      QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    mReceiver.setCanCancelDownload(true);
    LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver,
        new IntentFilter(QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    mReceiver.setListener(this);
  }

  @Override
  protected void onPause() {
    mReceiver.setListener(null);
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    super.onPause();
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
      int position = mRecyclerView.getChildPosition(v);
      if (position != RecyclerView.NO_POSITION) {
        SheikhInfo info = mAdapter.getSheikhInfoForPosition(position);
        if (info.downloadedSuras.size() != 114) {
          download(info, position);
        }
      }
    }
  };

  private void download(SheikhInfo sheikhInfo, int position) {
    String baseUri = mBasePath + sheikhInfo.path;
    boolean isGapless = AudioManagerUtils.isGapless(sheikhInfo.path);

    String sheikhName = mAdapter.getSheikhNameForPosition(position);
    Intent intent = ServiceIntentHelper.getDownloadIntent(this,
        AudioUtils.getQariUrl(this, position, true),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY, QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, new QuranAyah(1, 1));
    intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, new QuranAyah(114, 6));
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless);
    startService(intent);

    AudioManagerUtils.clearCacheKeyForSheikh(sheikhInfo.path);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void handleDownloadSuccess() {
    getShuyookhData();
  }

  @Override
  public void handleDownloadFailure(int errId) {
    getShuyookhData();
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

      SheikhInfo info = getSheikhInfoForPosition(position);
      int fullyDownloaded = info.downloadedSuras.size();
      holder.quantity.setText(
          getResources().getQuantityString(R.plurals.files_downloaded,
            fullyDownloaded, fullyDownloaded));
    }

    public SheikhInfo getSheikhInfoForPosition(int position) {
      return mDownloadInfoMap.get(mShuyookhPaths[position]);
    }

    public String getSheikhNameForPosition(int position) {
      return mShuyookhNames[position];
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
      itemView.setOnClickListener(mOnClickListener);
    }
  }
}
