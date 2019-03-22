package com.quran.labs.androidquran.ui;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.util.AudioManagerUtils;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QariDownloadInfo;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

public class SurahAudioManager extends QuranActionBarActivity
    implements DefaultDownloadReceiver.SimpleDownloadListener{

  private ProgressBar progressBar;
  private Disposable disposable;
  private RecyclerView recyclerView;
  private String basePath;
  private List<QariItem> qariItems;
  private SurahAdapter surahAdapter;


  @Inject
  QuranInfo quranInfo;
  @Inject
  QuranFileUtils quranFileUtils;
  @Inject
  AudioUtils audioUtils;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    QuranApplication quranApp = (QuranApplication) getApplication();
    quranApp.getApplicationComponent().inject(this);
    quranApp.refreshLocale(this, false);

    super.onCreate(savedInstanceState);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setTitle(R.string.audio_manager);
      ab.setDisplayHomeAsUpEnabled(true);
    }

    setContentView(R.layout.activity_surah_audio_manager);

    recyclerView = findViewById(R.id.recycler_view);
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setItemAnimator(new DefaultItemAnimator());

    progressBar = findViewById(R.id.progress);
    qariItems = audioUtils.getQariList(this);
    basePath = quranFileUtils.getQuranAudioDirectory(this);
    surahAdapter = new SurahAdapter(qariItems);
    recyclerView.setAdapter(surahAdapter);
    getShuyookhData();
  }

  private DisposableSingleObserver<List<QariDownloadInfo>> mOnDownloadInfo =
      new DisposableSingleObserver<List<QariDownloadInfo>>() {
        @Override
        public void onSuccess(List<QariDownloadInfo> downloadInfo) {
          progressBar.setVisibility(View.GONE);
//          shuyookhAdapter.setDownloadInfo(downloadInfo);
//          shuyookhAdapter.notifyDataSetChanged();
        }

        @Override
        public void onError(Throwable e) {
        }
      };

  @Override
  public void handleDownloadSuccess() {

  }

  @Override
  public void handleDownloadFailure(int errId) {

  }

  private void getShuyookhData() {
    if (disposable != null) {
      disposable.dispose();
    }
    disposable = AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, qariItems)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(mOnDownloadInfo);
  }

  private View.OnClickListener mOnClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      int position = recyclerView.getChildAdapterPosition(v);
      if (position != RecyclerView.NO_POSITION) {
//        QariDownloadInfo info = shuyookhAdapter.getSheikhInfoForPosition(position);
//        if (info.downloadedSuras.size() != 114) {
//          download(qariItems.get(position));
//        }
      }
    }
  };


  private class SurahAdapter extends RecyclerView.Adapter<SurahAudioManager.SurahViewHolder> {
    private final LayoutInflater mInflater;
    private final List<QariItem> mQariItems;
    private final Map<QariItem, QariDownloadInfo> mDownloadInfoMap;

    SurahAdapter(List<QariItem> items) {
      mQariItems = items;
      mDownloadInfoMap = new HashMap<>();
      mInflater = LayoutInflater.from(SurahAudioManager.this);
    }

    void setDownloadInfo(List<QariDownloadInfo> downloadInfo) {
      for (QariDownloadInfo info : downloadInfo) {
        mDownloadInfoMap.put(info.qariItem, info);
      }
    }

    @Override
    public SurahAudioManager.SurahViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new SurahAudioManager.SurahViewHolder(mInflater.inflate(R.layout.audio_manager_row, parent, false));
    }

    @Override
    public void onBindViewHolder(SurahAudioManager.SurahViewHolder holder, int position) {
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


  private class SurahViewHolder extends RecyclerView.ViewHolder {
    public final TextView name;
    public final TextView quantity;
    public final ImageView image;

    SurahViewHolder(View itemView) {
      super(itemView);
      name = itemView.findViewById(R.id.name);
      quantity = itemView.findViewById(R.id.quantity);

      image = itemView.findViewById(R.id.image);
      itemView.setOnClickListener(mOnClickListener);
    }
  }
}
