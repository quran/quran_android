package com.quran.labs.androidquran.ui.bottomsheet

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.QuranSettings

class PageThemeBottomSheet : BottomSheetDialogFragment() {

  interface OnThemeSelectedListener {
    fun onThemeSelected(theme: PageTheme)
  }

  private var selectedListener: OnThemeSelectedListener? = null
  private lateinit var currentTheme: PageTheme
  private val themes = PageTheme.getAllThemes()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val themeKey = QuranSettings.getInstance(requireContext()).pageTheme
    currentTheme = PageTheme.fromKey(themeKey)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.bottom_sheet_page_theme, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val recyclerView = view.findViewById<RecyclerView>(R.id.themes_recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    recyclerView.adapter = ThemeAdapter()
  }

  fun setOnThemeSelectedListener(listener: OnThemeSelectedListener) {
    selectedListener = listener
  }

  private fun onThemeClicked(theme: PageTheme) {
    currentTheme = theme
    QuranSettings.getInstance(requireContext()).pageTheme = theme.storageKey

    selectedListener?.onThemeSelected(theme)
    dismiss()
  }

  private inner class ThemeAdapter : RecyclerView.Adapter<ThemeViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
      val view =
        LayoutInflater.from(parent.context).inflate(R.layout.item_page_theme, parent, false)
      return ThemeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
      holder.bind(themes[position])
    }

    override fun getItemCount(): Int = themes.size
  }

  private inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val themeName: TextView = itemView.findViewById(R.id.theme_name)
    private val colorPreview: View = itemView.findViewById(R.id.color_preview)
    private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)

    fun bind(theme: PageTheme) {
      themeName.text = theme.getDisplayName(itemView.context)

      val isDarkMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
      colorPreview.setBackgroundColor(theme.getBackgroundColor(itemView.context, isDarkMode))

      radioButton.isChecked = theme.storageKey == currentTheme.storageKey

      itemView.setOnClickListener {
        onThemeClicked(theme)
      }
    }
  }

  companion object {
    const val TAG = "PageThemeBottomSheet"
  }
}
