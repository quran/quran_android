package com.quran.labs.androidquran.ui.adapter

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationRowData

import java.util.ArrayList

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.UnicastSubject

class TranslationsAdapter(private val downloadedMenuActionListener: DownloadedMenuActionListener) :
  RecyclerView.Adapter<TranslationsAdapter.TranslationViewHolder>() {

  private var downloadedItemActionListener: DownloadedItemActionListener = DownloadedItemActionListenerImpl()

  private val onClickDownloadSubject = UnicastSubject.create<TranslationRowData>()
  private val onClickRemoveSubject = UnicastSubject.create<TranslationRowData>()
  private val onClickRankUpSubject = UnicastSubject.create<TranslationRowData>()
  private val onClickRankDownSubject = UnicastSubject.create<TranslationRowData>()

  private var translations: List<TranslationRowData> = ArrayList()

  private var selectedItem: TranslationItem? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationsAdapter.TranslationViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
    return TranslationViewHolder(view, viewType)
  }

  override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
    val rowItem = translations[position]
    holder.apply {
      when (itemViewType) {
        R.layout.translation_row -> {
          val item = rowItem as TranslationItem
          this.translationItem = item
          itemView.isActivated =
            (selectedItem != null) && (item.translation.id == selectedItem?.translation?.id)
          translationTitle?.text = item.name()

          if (TextUtils.isEmpty(item.translation.translatorNameLocalized)) {
            holder.translationInfo?.text = item.translation.translator
          } else {
            holder.translationInfo?.text = item.translation.translatorNameLocalized
          }

          val leftImage = this.leftImage
          val rightImage = this.rightImage

          if (item.exists()) {
            rightImage?.visibility = View.GONE
            itemView.setOnLongClickListener(actionMenuListener)
            if (item.needsUpgrade()) {
              leftImage?.setImageResource(com.quran.mobile.feature.downloadmanager.R.drawable.ic_download)
              leftImage?.visibility = View.VISIBLE
              translationInfo?.setText(R.string.update_available)
            } else {
              leftImage?.visibility = View.GONE
            }
          } else {
            leftImage?.visibility = View.GONE
            rightImage?.setImageResource(com.quran.mobile.feature.downloadmanager.R.drawable.ic_download)
            rightImage?.setOnClickListener(null)
            rightImage?.visibility = View.VISIBLE
            rightImage?.isClickable = false
            rightImage?.contentDescription = null
          }
        }
        R.layout.translation_sep -> {
          itemView.isActivated = false
          separatorText?.text = rowItem.name()
        }
      }
    }
  }

  override fun getItemCount(): Int {
    return translations.size
  }

  override fun getItemViewType(position: Int): Int {
    return if (translations[position].isSeparator()) {
      R.layout.translation_sep
    } else {
      R.layout.translation_row
    }
  }

  fun getOnClickDownloadSubject(): Observable<TranslationRowData> {
    return onClickDownloadSubject.hide()
  }

  fun getOnClickRemoveSubject(): Observable<TranslationRowData> {
    return onClickRemoveSubject.hide()
  }

  fun getOnClickRankUpSubject(): Observable<TranslationRowData> {
    return onClickRankUpSubject.hide()
  }

  fun getOnClickRankDownSubject(): Observable<TranslationRowData> {
    return onClickRankDownSubject.hide()
  }

  fun setTranslations(data: List<TranslationRowData>) {
    this.translations = data
  }

  fun getTranslations(): List<TranslationRowData> {
    return translations
  }

  fun setSelectedItem(selectedItem: TranslationItem?) {
    this.selectedItem = selectedItem
    notifyDataSetChanged()
  }

  inner class DownloadedItemActionListenerImpl : DownloadedItemActionListener {
    override fun handleDeleteItemAction() {
      selectedItem?.let { item -> onClickRemoveSubject.onNext(item) }
    }

    override fun handleRankUpItemAction() {
      selectedItem?.let { item -> onClickRankUpSubject.onNext(item) }
    }

    override fun handleRankDownItemAction() {
      selectedItem?.let { item -> onClickRankDownSubject.onNext(item) }
    }
  }

  inner class TranslationViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
    var translationTitle: TextView? = itemView.findViewById(R.id.translation_title)
    var translationInfo: TextView? = itemView.findViewById(R.id.translation_info)
    var leftImage: ImageView? = itemView.findViewById(R.id.left_image)
    var rightImage: ImageView? = itemView.findViewById(R.id.right_image)
    var separatorText: TextView? = itemView.findViewById(R.id.separator_txt)
    var translationItem: TranslationItem? = null

    init {
      if (viewType == R.layout.translation_row) {
        itemView.setOnClickListener {
          downloadedMenuActionListener.finishMenuAction()
          translationItem?.let { item ->
            if (!item.exists() || item.needsUpgrade()) {
              onClickDownloadSubject.onNext(item)
            }
          }
        }
      }
    }

    val actionMenuListener = OnLongClickListener {
      translationItem?.let { item -> downloadedMenuActionListener.startMenuAction(item, downloadedItemActionListener) }
      true
    }
  }
}
