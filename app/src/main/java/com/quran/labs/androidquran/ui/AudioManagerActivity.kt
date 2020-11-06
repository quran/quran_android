package com.quran.labs.androidquran.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.QariItem
import com.quran.labs.androidquran.util.AudioManagerUtils
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QariDownloadInfo
import com.quran.labs.androidquran.util.QuranFileUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.HashMap
import javax.inject.Inject

class AudioManagerActivity : QuranActionBarActivity() {
  private val disposable: CompositeDisposable = CompositeDisposable()

  private lateinit var progressBar: ProgressBar
  private lateinit var recyclerView: RecyclerView
  private lateinit var shuyookhAdapter: ShuyookhAdapter

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
      ab.setTitle(R.string.audio_manager)
      ab.setDisplayHomeAsUpEnabled(true)
    }
    setContentView(R.layout.audio_manager)

    qariItems = audioUtils.getQariList(this)
    shuyookhAdapter = ShuyookhAdapter(qariItems)

    recyclerView = findViewById(R.id.recycler_view)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.itemAnimator = DefaultItemAnimator()
    recyclerView.adapter = shuyookhAdapter

    progressBar = findViewById(R.id.progress)
    basePath = quranFileUtils.getQuranAudioDirectory(this)
  }

  private fun requestShuyookhData() {
    disposable.clear()

    disposable.add(
        AudioManagerUtils.shuyookhDownloadObservable(quranInfo, basePath, qariItems)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ downloadInfo ->
              progressBar.visibility = View.GONE
              shuyookhAdapter.setDownloadInfo(downloadInfo)
              shuyookhAdapter.notifyDataSetChanged()
            }, { })
    )
  }

  override fun onResume() {
    super.onResume()
    requestShuyookhData()
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

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private inner class ShuyookhAdapter internal constructor(val qariItems: List<QariItem>) :
      Adapter<SheikhViewHolder>() {
    private val inflater: LayoutInflater = LayoutInflater.from(this@AudioManagerActivity)
    private val downloadInfoMap: MutableMap<QariItem, QariDownloadInfo> = HashMap()

    fun setDownloadInfo(downloadInfo: List<QariDownloadInfo>) {
      for (info in downloadInfo) {
        downloadInfoMap[info.qariItem] = info
      }
    }

    override fun onCreateViewHolder(
      parent: ViewGroup,
      viewType: Int
    ): SheikhViewHolder {
      return SheikhViewHolder(inflater.inflate(R.layout.audio_manager_row, parent, false))
    }

    override fun onBindViewHolder(
      holder: SheikhViewHolder,
      position: Int
    ) {
      holder.name.text = qariItems[position].name
      val info = getSheikhInfoForPosition(position)
      val fullyDownloaded = info!!.downloadedSuras.size()
      holder.quantity.text = resources.getQuantityString(
          R.plurals.files_downloaded,
          fullyDownloaded, fullyDownloaded
      )
      if (fullyDownloaded > 0)
        holder.image.setBackgroundResource(R.drawable.downloaded_button_circle)
      else
        holder.image.setBackgroundResource(R.drawable.download_button_circle)
    }

    fun getSheikhInfoForPosition(position: Int): QariDownloadInfo? {
      return downloadInfoMap[qariItems[position]]
    }

    override fun getItemCount(): Int {
      return if (downloadInfoMap.isEmpty()) 0 else qariItems.size
    }
  }

  private inner class SheikhViewHolder internal constructor(itemView: View) :
      ViewHolder(itemView) {
    val name: TextView = itemView.findViewById(R.id.name)
    val quantity: TextView = itemView.findViewById(R.id.quantity)
    val image: ImageView = itemView.findViewById(R.id.image)

    init {
      itemView.setOnClickListener(onClickListener)
    }
  }
}
