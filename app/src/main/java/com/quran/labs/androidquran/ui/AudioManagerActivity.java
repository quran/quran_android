package com.quran.labs.androidquran.ui;

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

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QariDownloadInfo;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;


public class AudioManagerActivity extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener {
  private static final String AUDIO_DOWNLOAD_KEY = "AudioManager.DownloadKey";

  private ProgressBar progressBar;
  private Disposable disposable;
  private ShuyookhAdapter shuyookhAdapter;
  private RecyclerView recyclerView;
  private DefaultDownloadReceiver downloadReceiver;
  private String basePath;
  private List<QariItem> qariItems;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.refreshLocale(this, false);

    super.onCreate(savedInstanceState);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.audio_manager);
      ab.setDisplayHomeAsUpEnabled(true);
    }

    setContentView(R.layout.audio_manager);

    recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    qariItems = AudioUtils.getQariList(this);
    shuyookhAdapter = new ShuyookhAdapter(qariItems);
    recyclerView.setAdapter(shuyookhAdapter);

    progressBar = (ProgressBar) findViewById(R.id.progress);

    basePath = QuranFileUtils.getQuranAudioDirectory(this);
    getShuyookhData();
  }

  private void getShuyookhData() {
    if (disposable != null) {
      disposable.dispose();
    }
    disposable = AudioManagerUtils.shuyookhDownloadObservable(basePath, qariItems)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(mOnDownloadInfo);
  }

  @Override
  protected void onResume() {
    super.onResume();
    downloadReceiver = new DefaultDownloadReceiver(this,
      QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    downloadReceiver.setCanCancelDownload(true);
    LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver,
        new IntentFilter(QuranDownloadNotifier.ProgressIntent.INTENT_NAME));
    downloadReceiver.setListener(this);
  }

  @Override
  protected void onPause() {
    downloadReceiver.setListener(null);
    LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    disposable.dispose();
    super.onDestroy();
  }

  private DisposableSingleObserver<List<QariDownloadInfo>> mOnDownloadInfo =
      new DisposableSingleObserver<List<QariDownloadInfo>>() {
        @Override
        public void onSuccess(List<QariDownloadInfo> downloadInfo) {
          progressBar.setVisibility(View.GONE);
          shuyookhAdapter.setDownloadInfo(downloadInfo);
          shuyookhAdapter.notifyDataSetChanged();
        }

        @Override
        public void onError(Throwable e) {
        }
      };

  private View.OnClickListener mOnClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      int position = recyclerView.getChildAdapterPosition(v);
      if (position != RecyclerView.NO_POSITION) {
        QariDownloadInfo info = shuyookhAdapter.getSheikhInfoForPosition(position);
        if (info.downloadedSuras.size() != 114) {
          download(qariItems.get(position));
        }
      }
    }
  };

  private void download(QariItem qariItem) {
    String baseUri = basePath + qariItem.getPath();
    boolean isGapless = qariItem.isGapless();

    String sheikhName = qariItem.getName();
    Intent intent = ServiceIntentHelper.getDownloadIntent(this,
        AudioUtils.getQariUrl(qariItem),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY, QuranDownloadService.DOWNLOAD_TYPE_AUDIO);
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, new SuraAyah(1, 1));
    intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, new SuraAyah(114, 6));
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless);
    startService(intent);

    AudioManagerUtils.clearCacheKeyForSheikh(qariItem);
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
    private final List<QariItem> mQariItems;
    private final Map<QariItem, QariDownloadInfo> mDownloadInfoMap;

    ShuyookhAdapter(List<QariItem> items) {
      mQariItems = items;
      mDownloadInfoMap = new HashMap<>();
      mInflater = LayoutInflater.from(AudioManagerActivity.this);
    }

    void setDownloadInfo(List<QariDownloadInfo> downloadInfo) {
      for (QariDownloadInfo info : downloadInfo) {
        mDownloadInfoMap.put(info.qariItem, info);
      }
    }

    @Override
    public SheikhViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new SheikhViewHolder(mInflater.inflate(R.layout.audio_manager_row, parent, false));
    }

    @Override
    public void onBindViewHolder(SheikhViewHolder holder, int position) {
      holder.name.setText(mQariItems.get(position).getName());

      QariDownloadInfo info = getSheikhInfoForPosition(position);
      int fullyDownloaded = info.downloadedSuras.size();
      holder.quantity.setText(
          getResources().getQuantityString(R.plurals.files_downloaded,
            fullyDownloaded, fullyDownloaded));
    }

    QariDownloadInfo getSheikhInfoForPosition(int position) {
      return mDownloadInfoMap.get(mQariItems.get(position));
    }

    @Override
    public int getItemCount() {
      return mDownloadInfoMap.size() == 0 ? 0 : mQariItems.size();
    }
  }

  private class SheikhViewHolder extends RecyclerView.ViewHolder {
    public final TextView name;
    public final TextView quantity;
    public final ImageView image;

    SheikhViewHolder(View itemView) {
      super(itemView);
      name = (TextView) itemView.findViewById(R.id.name);
      quantity = (TextView) itemView.findViewById(R.id.quantity);

      image = (ImageView) itemView.findViewById(R.id.image);
      itemView.setOnClickListener(mOnClickListener);
    }
  }
}
