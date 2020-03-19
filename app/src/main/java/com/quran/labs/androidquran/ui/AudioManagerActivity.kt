package com.quran.labs.androidquran.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R.id
import com.quran.labs.androidquran.R.layout
import com.quran.labs.androidquran.R.plurals
import com.quran.labs.androidquran.R.string
import com.quran.labs.androidquran.common.QariItem
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.data.SuraAyah
import com.quran.labs.androidquran.service.QuranDownloadService
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver.SimpleDownloadListener
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent
import com.quran.labs.androidquran.service.util.ServiceIntentHelper
import com.quran.labs.androidquran.util.AudioManagerUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QariDownloadInfo
import com.quran.labs.androidquran.util.QuranFileUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.HashMap
import javax.inject.Inject

class AudioManagerActivity : QuranActionBarActivity(), SimpleDownloadListener {
  private val disposable: CompositeDisposable = CompositeDisposable()

  private lateinit var progressBar: ProgressBar
  private lateinit var recyclerView: RecyclerView
  private lateinit var shuyookhAdapter: ShuyookhAdapter

  private var downloadReceiver: DefaultDownloadReceiver? = null
  private var basePath: String? = null

  private var qariItems: List<QariItem> = emptyList()

  @Inject
  lateinit var audioUtils: AudioUtils

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranFileUtils: QuranFileUtils

  override fun onCreate(savedInstanceState: Bundle?) {
    val quranApp = application as QuranApplication
    quranApp.applicationComponent
        .inject(this)
    quranApp.refreshLocale(this, false)

    super.onCreate(savedInstanceState)

    val ab = supportActionBar
    if (ab != null) {
      ab.setTitle(string.audio_manager)
      ab.setDisplayHomeAsUpEnabled(true)
    }
    setContentView(layout.audio_manager)

    qariItems = audioUtils.getQariList(this)
    shuyookhAdapter = ShuyookhAdapter(qariItems)

    recyclerView = findViewById(id.recycler_view)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.itemAnimator = DefaultItemAnimator()
    recyclerView.adapter = shuyookhAdapter

    progressBar = findViewById(id.progress)
    basePath = quranFileUtils.getQuranAudioDirectory(this)
    requestShuyookhData()
  }

  private fun requestShuyookhData() {
      disposable.clear()

      disposable.add(AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, qariItems)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe({ downloadInfo ->
            progressBar.visibility = View.GONE
            shuyookhAdapter.setDownloadInfo(downloadInfo)
            shuyookhAdapter.notifyDataSetChanged()
          },{ })
      )
    }

  override fun onResume() {
    super.onResume()
    val receiver = DefaultDownloadReceiver(
        this,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    receiver.setCanCancelDownload(true)
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            receiver,
            IntentFilter(ProgressIntent.INTENT_NAME)
        )
    receiver.setListener(this)
    downloadReceiver = receiver
  }

  override fun onPause() {
    downloadReceiver?.let { downloadReceiver ->
      downloadReceiver.setListener(null)
      LocalBroadcastManager.getInstance(this)
          .unregisterReceiver(downloadReceiver)
    }
    downloadReceiver = null
    super.onPause()
  }

  override fun onDestroy() {
    disposable.dispose()
    super.onDestroy()
  }

  private val onClickListener =
    OnClickListener { v ->
      val position = recyclerView.getChildAdapterPosition(v)
      if (position != RecyclerView.NO_POSITION) {
        val qariItem = shuyookhAdapter.qariItems[position]
        val intent =
          Intent(this@AudioManagerActivity, SheikhAudioManagerActivity::class.java)
        intent.putExtra(SheikhAudioManagerActivity.EXTRA_SHEIKH, qariItem)
        startActivity(intent)
      }
    }

  private fun download(qariItem: QariItem) {
    val baseUri = basePath + qariItem.path
    val isGapless = qariItem.isGapless
    val sheikhName = qariItem.name
    val intent = ServiceIntentHelper.getDownloadIntent(
        this,
        audioUtils.getQariUrl(qariItem),
        baseUri, sheikhName, AUDIO_DOWNLOAD_KEY,
        QuranDownloadService.DOWNLOAD_TYPE_AUDIO
    )
    intent.putExtra(QuranDownloadService.EXTRA_START_VERSE, SuraAyah(1, 1))
    intent.putExtra(QuranDownloadService.EXTRA_END_VERSE, SuraAyah(114, 6))
    intent.putExtra(QuranDownloadService.EXTRA_IS_GAPLESS, isGapless)
    startService(intent)
    AudioManagerUtils.clearCacheKeyForSheikh(qariItem)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun handleDownloadSuccess() {
    requestShuyookhData()
  }

  override fun handleDownloadFailure(errId: Int) {
    requestShuyookhData()
  }

  private inner class ShuyookhAdapter internal constructor(val qariItems: List<QariItem>) :
      Adapter<SheikhViewHolder>() {
    private val inflater: LayoutInflater
    private val downloadInfoMap: MutableMap<QariItem, QariDownloadInfo>
    fun setDownloadInfo(downloadInfo: List<QariDownloadInfo>) {
      for (info in downloadInfo) {
        downloadInfoMap[info.qariItem] = info
      }
    }

    override fun onCreateViewHolder(
      parent: ViewGroup,
      viewType: Int
    ): SheikhViewHolder {
      return SheikhViewHolder(inflater.inflate(layout.audio_manager_row, parent, false))
    }

    override fun onBindViewHolder(
      holder: SheikhViewHolder,
      position: Int
    ) {
      holder.name.text = qariItems[position].name
      val info = getSheikhInfoForPosition(position)
      val fullyDownloaded = info!!.downloadedSuras.size()
      holder.quantity.text = resources.getQuantityString(
          plurals.files_downloaded,
          fullyDownloaded, fullyDownloaded
      )
    }

    fun getSheikhInfoForPosition(position: Int): QariDownloadInfo? {
      return downloadInfoMap[qariItems[position]]
    }

    override fun getItemCount(): Int {
      return if (downloadInfoMap.isEmpty()) 0 else qariItems.size
    }

    init {
      downloadInfoMap = HashMap()
      inflater = LayoutInflater.from(this@AudioManagerActivity)
    }
  }

  private inner class SheikhViewHolder internal constructor(itemView: View) :
      ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(id.name)
    val quantity: TextView = itemView.findViewById(id.quantity)
    val image: ImageView = itemView.findViewById(id.image)

    init {
      itemView.setOnClickListener(onClickListener)
    }
  }

  companion object {
    private const val AUDIO_DOWNLOAD_KEY = "AudioManager.DownloadKey"
  }
}
