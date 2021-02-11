package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.R.*
import com.quran.labs.androidquran.ui.SheikhAudioManagerActivity
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.ForceCompleteTextView
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import kotlin.math.max

/**
 * [BulkDownloadFragment] is a dialog for selecting audio download first Surah and last Surah
 */
class BulkDownloadFragment : DialogFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  private lateinit var suraFirstInput: ForceCompleteTextView
  private lateinit var suraLastInput: ForceCompleteTextView

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity = requireActivity()
    val inflater = activity.layoutInflater

    @SuppressLint("InflateParams")
    val layout = inflater.inflate(layout.bulk_download_dialog, null)

    val builder = Builder(activity)
    builder.setTitle(activity.getString(string.audio_manager_download_all))

    // First Sura chooser
    suraFirstInput = layout.findViewById(R.id.first_sura_spinner)
    val suras = activity.resources.getStringArray(array.sura_names)
        .mapIndexed { index: Int, sura: String? ->
          QuranUtils.getLocalizedNumber(activity, index + 1) + ". " + sura
        }

    val suraFirstAdapter = InfixFilterArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item, suras
    )
    suraFirstInput.setAdapter(suraFirstAdapter)

    suraFirstInput.setOnForceCompleteListener { _: ForceCompleteTextView?, position: Int, _: Long ->
      val enteredFirstText = suraFirstInput.text.toString()
      val suraFirstName: String?

      suraFirstName = when {
        // user selects
        position >= 0 -> { suraFirstAdapter.getItem(position) }
        suras.contains(enteredFirstText) -> { enteredFirstText }
        // leave to the next code
        suraFirstAdapter.isEmpty -> { null }
        // maybe first initialization or invalid input
        else -> { suraFirstAdapter.getItem(0) }
      }

      // default to Al-Fatiha
      val suraFirst = max(suras.indexOf(suraFirstName) + 1, 1)
      suraFirstInput.tag = suraFirst
      suraFirstInput.setText(suras[suraFirst - 1])
    }

    // Last Sura chooser
    suraLastInput = layout.findViewById(R.id.last_sura_spinner)

    val suraLastAdapter = InfixFilterArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item, suras
    )
    suraLastInput.setAdapter(suraLastAdapter)

    suraLastInput.setOnForceCompleteListener { _: ForceCompleteTextView?, position: Int, _: Long ->
      val enteredLastText = suraLastInput.text.toString()
      val suraLastName: String?

      suraLastName = when {
        // user selects
        position >= 0 -> { suraLastAdapter.getItem(position) }
        suras.contains(enteredLastText) -> { enteredLastText }
        // leave to the next code
        suraLastAdapter.isEmpty -> { null }
        // maybe first initialization or invalid input
        else -> { suraLastAdapter.getItem(0) }
      }

      var suraLast = suras.indexOf(suraLastName) + 1
      // default to An-Nas
      if (suraLast == 0 || suraLast == 1) suraLast = 114
      suraLastInput.tag = suraLast
      suraLastInput.setText(suras[suraLast - 1])
    }

    builder.setView(layout)
    builder.setPositiveButton(
        getString(string.audio_manager_download_selection)
    ) { _: DialogInterface?, _: Int ->
      // trigger sura completion
      layout.requestFocus()
      dismiss()
      onSubmit()
    }
    return builder.create()
  }

  private fun onSubmit() {
    try {
      val selectedFirstSura = suraFirstInput.tag as Int
      val selectedLastSura = suraLastInput.tag as Int
      if (selectedFirstSura < selectedLastSura)
        (activity as? SheikhAudioManagerActivity)?.downloadBulk(selectedFirstSura,selectedLastSura)
      else
        (activity as? SheikhAudioManagerActivity)?.downloadBulk(selectedLastSura,selectedFirstSura)
    } catch (e: Exception) {
      Timber.d(e, "Could not bulk download, something went wrong...")
    }
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    dialog?.window?.setSoftInputMode(
        LayoutParams.SOFT_INPUT_STATE_VISIBLE or LayoutParams.SOFT_INPUT_ADJUST_PAN
    )
  }

  /**
   * ListAdapter that supports filtering by using case-insensitive infix (substring).
   */
  private class InfixFilterArrayAdapter(
    context: Context,
    @LayoutRes private val itemLayoutRes: Int,
    private val originalItems: List<String>
  ) : BaseAdapter(), Filterable {
    private var items: List<String>
    private val inflater: LayoutInflater
    private val filter: Filter = ItemFilter()

    init {
      this.items = originalItems
      inflater = LayoutInflater.from(context)
    }

    override fun getCount() = items.size

    override fun getItem(position: Int) = items[position]

    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val view = convertView ?: inflater.inflate(itemLayoutRes, parent, false)

      // As no fieldId is known/assigned, assume it is a TextView
      val text = view as TextView
      text.text = getItem(position)
      return view
    }

    override fun getFilter() = filter

    /**
     * Filter that do filtering by matching case-insensitive infix of the input.
     */
    private inner class ItemFilter : Filter() {
      override fun performFiltering(constraint: CharSequence?): FilterResults {
        val results = FilterResults()

        // The items never change after construction, not sure if really needs to copy
        if (constraint == null || constraint.isEmpty()) {
          results.values = originalItems
          results.count = originalItems.size
        } else {
          val infix = constraint.toString().toLowerCase(Locale.getDefault())
          val filteredIndex = infix.toIntOrNull()?.toString()
          val filteredCopy = originalItems.filterIndexed { index, sura ->
            sura.toLowerCase(Locale.getDefault()).contains(infix) ||
                // support English numbers in Arabic mode
                filteredIndex != null && (index + 1).toString().contains(filteredIndex)
          }
          results.values = filteredCopy
          results.count = filteredCopy.size
        }
        return results
      }

      override fun publishResults(constraint: CharSequence, results: FilterResults) {
        items = results.values as List<String>
        if (results.count > 0) {
          notifyDataSetChanged()
        } else {
          notifyDataSetInvalidated()
        }
      }
    }
  }

  companion object {
    const val TAG = "BulkDownloadFragment"
  }
}
