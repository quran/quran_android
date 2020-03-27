package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
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
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.R.array
import com.quran.labs.androidquran.R.layout
import com.quran.labs.androidquran.R.string
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranInfo
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.widgets.ForceCompleteTextView
import timber.log.Timber
import java.util.ArrayList
import java.util.Arrays
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class JumpFragment : DialogFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity: Activity? = activity
    val inflater = activity!!.layoutInflater

    @SuppressLint("InflateParams")
    val layout = inflater.inflate(layout.jump_dialog, null)

    val builder = Builder(activity)
    builder.setTitle(activity.getString(string.menu_jump))

    // Sura chooser
    val suraInput: ForceCompleteTextView = layout.findViewById(R.id.sura_spinner)
    val suras = activity.resources.getStringArray(array.sura_names)
    val sb = StringBuilder()
    for (i in suras.indices) {
      sb.append(QuranUtils.getLocalizedNumber(activity, i + 1))
      sb.append(". ")
      sb.append(suras[i])
      suras[i] = sb.toString()
      sb.setLength(0)
    }

    val suraAdapter = InfixFilterArrayAdapter(
        activity,
        android.R.layout.simple_spinner_dropdown_item, suras
    )
    suraInput.setAdapter(suraAdapter)

    // Ayah chooser
    val ayahInput = layout.findViewById<EditText>(R.id.ayah_spinner)

    // Page chooser
    val pageInput = layout.findViewById<EditText>(R.id.page_number)
    pageInput.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_GO) {
        dismiss()
        goToPage(pageInput.text.toString())
        true
      } else {
        false
      }
    }
    suraInput.setOnForceCompleteListener { v: ForceCompleteTextView?, position: Int, rowId: Long ->
      val suraList = listOf(*suras)
      val enteredText = suraInput.text.toString()
      val suraName: String?

      suraName = when {
        // user selects
        position >= 0 -> { suraAdapter.getItem(position) }
        suraList.contains(enteredText) -> { enteredText }
        // leave to the next code
        suraAdapter.isEmpty -> { null }
        // maybe first initialization or invalid input
        else -> { suraAdapter.getItem(0) }
      }

      var sura = suraList.indexOf(suraName) + 1
      // default to al-Fatiha
      if (sura == 0) sura = 1
      suraInput.tag = sura
      suraInput.setText(suras[sura - 1])

      //  trigger ayah change
      val ayahValue: CharSequence = ayahInput.text
      // space is intentional, to differentiate with value set by the user (delete/backspace)
      ayahInput.setText(if (ayahValue.isNotEmpty()) ayahValue else " ")
    }
    ayahInput.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

      override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

      override fun afterTextChanged(s: Editable) {
        val context: Context? = getActivity()
        val ayahString = s.toString()
        var ayah = ayahString.toIntOrNull() ?: 1
        val suraTag = suraInput.tag
        if (suraTag != null) {
          val sura = suraTag as Int
          val ayahCount = quranInfo.getNumAyahs(sura)
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
    })
    builder.setView(layout)
    builder.setPositiveButton(
        getString(string.dialog_ok)
    ) { _: DialogInterface?, _: Int ->
      try {
        layout.requestFocus() // trigger sura completion
        dismiss()
        var pageStr = pageInput.text.toString()
        if (TextUtils.isEmpty(pageStr)) {
          pageStr = pageInput.hint.toString()
          val page = pageStr.toInt()
          val selectedSura = suraInput.tag as Int
          val selectedAyah = ayahInput.tag as Int
          (activity as? JumpDestination)?.jumpToAndHighlight(page, selectedSura, selectedAyah)
        } else {
          goToPage(pageStr)
        }
      } catch (e: Exception) {
        Timber.d(e, "Could not jump, something went wrong...")
      }
    }
    return builder.create()
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

  private fun goToPage(text: String) {
    val page = text.toIntOrNull() ?: return

    // user has interacted with 'Go to page' field, so we
    // need to verify if the input number is within
    // the acceptable range
    if (page < Constants.PAGES_FIRST || page > quranInfo.numberOfPages) {
      // maybe show a toast message?
      return
    }
    (activity as? JumpDestination)?.jumpTo(page)
  }

  /**
   * ListAdapter that supports filtering by using case-insensitive infix (substring).
   */
  private class InfixFilterArrayAdapter internal constructor(
    context: Context,
    @LayoutRes itemLayoutRes: Int,
    items: Array<String>
  ) : BaseAdapter(), Filterable {
    // May be extracted to other package
    private val originalItems: List<String> = listOf(*items)
    private var items: List<String>
    private val inflater: LayoutInflater
    private val itemLayoutRes: Int
    private val filter: Filter = ItemFilter()
    private val lock = Any()

    init {
      this.items = originalItems
      inflater = LayoutInflater.from(context)
      this.itemLayoutRes = itemLayoutRes
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
        var copy: ArrayList<String>
        synchronized(lock) { copy = ArrayList(originalItems) }
        if (constraint == null || constraint.isEmpty()) {
          results.values = copy
          results.count = copy.size
        } else {
          val infix = constraint.toString().toLowerCase(Locale.getDefault())
          val filteredCopy = mutableListOf<String>()
          for (i in copy) {
            val value = i.toLowerCase(Locale.getDefault())
            if (value.contains(infix)) {
              filteredCopy.add(i)
            }
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
    const val TAG = "JumpFragment"
  }
}
