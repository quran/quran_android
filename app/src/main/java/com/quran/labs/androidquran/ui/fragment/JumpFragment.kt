package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import com.quran.common.search.SearchTextUtil
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.ForceCompleteTextView
import timber.log.Timber
import javax.inject.Inject

/**
 * [DialogFragment] of a dialog for quickly selecting and jumping to a particular location in the
 * Quran. A location can be selected by page number or Surah/Ayah.
 */
class JumpFragment : DialogFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo
  private var suppressJump: Boolean = false

  private lateinit var suraInput: ForceCompleteTextView
  private lateinit var ayahInput: EditText
  private lateinit var pageInput: EditText

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity: Activity = requireActivity()
    val inflater = activity.layoutInflater

    @SuppressLint("InflateParams")
    val layout = inflater.inflate(R.layout.jump_dialog, null)

    val builder = Builder(activity)
    builder.setTitle(activity.getString(R.string.menu_jump))

    // Sura chooser
    suraInput = layout.findViewById(R.id.sura_spinner)
    val suras = activity.resources.getStringArray(R.array.sura_names)
        .mapIndexed { index: Int, sura: String? ->
          QuranUtils.getLocalizedNumber(activity, index + 1) + ". " + sura
        }

    val suraAdapter = InfixFilterArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item, suras
    )
    suraInput.setAdapter(suraAdapter)

    // Ayah chooser
    ayahInput = layout.findViewById(R.id.ayah_spinner)

    // Page chooser
    pageInput = layout.findViewById(R.id.page_number)
    pageInput.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_GO) {
        dismiss()
        onSubmit()
        true
      } else {
        false
      }
    }

    pageInput.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

      override fun afterTextChanged(s: Editable?) {
        val number = s.toString().toIntOrNull() ?: return
        val pageNumber = number.coerceIn(1..quranInfo.numberOfPages)
        val sura = quranInfo.getSuraOnPage(pageNumber)
        val ayah = quranInfo.getFirstAyahOnPage(pageNumber)

        suppressJump = true
        suraInput.setText(suras[sura - 1])
        suraInput.tag = sura
        ayahInput.setText(ayah.toString())
        suppressJump = false
      }
    })

    suraInput.setOnForceCompleteListener { _: ForceCompleteTextView?, position: Int, _: Long ->
      val enteredText = suraInput.text.toString()

      val suraName: String? = when {
        // user selects
        position >= 0 -> { suraAdapter.getItem(position) }
        suras.contains(enteredText) -> { enteredText }
        // leave to the next code
        suraAdapter.isEmpty -> { null }
        // maybe first initialization or invalid input
        else -> { suraAdapter.getItem(0) }
      }

      var sura = suras.indexOf(suraName) + 1
      // default to al-Fatiha
      if (sura == 0) sura = 1
      suraInput.tag = sura
      suraInput.setText(suras[sura - 1])

      //  trigger ayah change
      val ayahValue: CharSequence = ayahInput.text
      // space is intentional, to differentiate with value set by the user (delete/backspace)
      ayahInput.setText(ayahValue.ifEmpty { " " })
    }

    ayahInput.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

      override fun afterTextChanged(s: Editable) {
        val context: Context? = getActivity()
        val ayahString = s.toString()
        var ayah = ayahString.toIntOrNull() ?: 1
        if (suppressJump) {
          ayahInput.tag = ayah
        } else {
          val suraTag = suraInput.tag
          if (suraTag != null) {
            val sura = suraTag as Int
            val ayahCount = quranInfo.getNumberOfAyahs(sura)
            // ensure in 1..ayahCount
            ayah = ayah.coerceIn(1..ayahCount)
            val page = quranInfo.getPageFromSuraAyah(sura, ayah)
            pageInput.hint = QuranUtils.getLocalizedNumber(context, page)
            pageInput.text = null
          }
          ayahInput.tag = ayah
          // seems numeric IM always use western arabic (not localized)
          val correctText = ayah.toString()
          // empty input means the user clears the input, we don't force to fill it, let him type
          if (s.isNotEmpty() && correctText != ayahString) {
            s.replace(0, s.length, correctText)
          }
        }
      }
    })

    builder.setView(layout)
    builder.setPositiveButton(
        getString(R.string.dialog_ok)
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
      val pageStr = pageInput.text.toString()
      val page = if (pageStr.isEmpty()) {
        pageInput.hint.toString().toIntOrNull()
      } else {
        pageStr.toIntOrNull()
      }

      if (page != null) {
        val selectedSura = suraInput.tag as Int
        val selectedAyah = ayahInput.tag as Int
        (activity as? JumpDestination)?.jumpToAndHighlight(page, selectedSura, selectedAyah)
      }
    } catch (e: Exception) {
      Timber.d(e, "Could not jump, something went wrong...")
    }
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent
        .inject(this)
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
    private val isRtl = SearchTextUtil.isRtl(originalItems.first())
    private val searchPreparedItems = originalItems.map { prepareForSearch(it, isRtl) }

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

    private fun prepareForSearch(input: String, isRtl: Boolean): String {
      return SearchTextUtil.asSearchableString(input, isRtl)
    }

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
          val infix = cleanUpQueryString(constraint.toString())
          val filteredIndex = infix.toIntOrNull()?.toString()
          val filteredCopy = originalItems.filterIndexed { index, sura ->
            searchPreparedItems[index].contains(infix) ||
                // support English numbers in Arabic mode
                filteredIndex != null && (index + 1).toString().contains(filteredIndex)
          }
          results.values = filteredCopy
          results.count = filteredCopy.size
        }
        return results
      }

      private fun cleanUpQueryString(query: String): String {
        return if (SearchTextUtil.isRtl(query)) {
          SearchTextUtil.asSearchableString(query, true)
        } else {
          query.lowercase()
        }
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
    const val TAG = "JumpFragment"
  }
}
