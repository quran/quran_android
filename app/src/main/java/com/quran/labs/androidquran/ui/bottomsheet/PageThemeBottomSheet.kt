package com.quran.labs.androidquran.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.ThemeUtil

class PageThemeBottomSheet : BottomSheetDialogFragment() {

    private lateinit var currentTheme: PageTheme
    private val themes = PageTheme.getAllThemes()
    private var themesRecyclerView: RecyclerView? = null

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

        val quranSettings = QuranSettings.getInstance(requireContext())
        setupAppThemeDropdown(view, quranSettings)
        setupThemesGrid(view, quranSettings)
    }

    private fun setupAppThemeDropdown(view: View, quranSettings: QuranSettings) {
        val spinner = view.findViewById<Spinner>(R.id.app_theme_spinner)
        val appThemes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, appThemes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentAppTheme = quranSettings.currentTheme()
        val selectedIndex = when (currentAppTheme) {
            Constants.THEME_LIGHT -> 0
            Constants.THEME_DARK -> 1
            else -> 2
        }

        spinner.setSelection(selectedIndex, false)
        var lastSelectedPosition = selectedIndex
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, position: Int, id: Long) {
                if (position == lastSelectedPosition) return
                lastSelectedPosition = position

                val newTheme = when (position) {
                    0 -> Constants.THEME_LIGHT
                    1 -> Constants.THEME_DARK
                    else -> Constants.THEME_DEFAULT
                }
                quranSettings.setAppTheme(newTheme)
                ThemeUtil.setTheme(newTheme, quranSettings.pageTheme)

                // since this changes the app's overall theme, it's safer to recreate the activity
                requireActivity().recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupThemesGrid(view: View, quranSettings: QuranSettings) {
        themesRecyclerView = view.findViewById(R.id.themes_recycler_view)
        themesRecyclerView?.layoutManager = GridLayoutManager(requireContext(), 3)
        themesRecyclerView?.adapter = ThemeCardAdapter(quranSettings)
    }

    private fun onThemeClicked(theme: PageTheme, quranSettings: QuranSettings) {
        val previousPageTheme = quranSettings.pageTheme
        val previousPosition = themes.indexOfFirst { it.storageKey == previousPageTheme }
        val newPosition = themes.indexOf(theme)

        if (previousPageTheme == theme.storageKey) return

        currentTheme = theme
        quranSettings.pageTheme = theme.storageKey
        ThemeUtil.setTheme(quranSettings.currentTheme(), theme.storageKey)

        val adapter = themesRecyclerView?.adapter
        if (previousPosition >= 0) adapter?.notifyItemChanged(previousPosition)
        if (newPosition >= 0 && newPosition != previousPosition) adapter?.notifyItemChanged(newPosition)

        // quiet theme forces dark mode, so we might need to recreate activity
        if (theme.storageKey == Constants.PAGE_THEME_QUIET || previousPageTheme == Constants.PAGE_THEME_QUIET) {
            requireActivity().recreate()
        }
    }

    private inner class ThemeCardAdapter(private val quranSettings: QuranSettings) : RecyclerView.Adapter<ThemeCardViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeCardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page_theme_card, parent, false)
            return ThemeCardViewHolder(view, quranSettings)
        }

        override fun onBindViewHolder(holder: ThemeCardViewHolder, position: Int) {
            holder.bind(themes[position])
        }

        override fun getItemCount(): Int = themes.size
    }

    private inner class ThemeCardViewHolder(itemView: View, private val quranSettings: QuranSettings) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.theme_card)
        private val arabicText: TextView = itemView.findViewById(R.id.arabic_preview)
        private val themeName: TextView = itemView.findViewById(R.id.theme_name)
        private val selectionBorder: View = itemView.findViewById(R.id.selection_border)

        fun bind(theme: PageTheme) {
            val textColor = theme.getPreviewTextColor(itemView.context)
            cardView.setCardBackgroundColor(theme.getBackgroundColor(itemView.context))
            arabicText.setTextColor(textColor)
            themeName.setTextColor(textColor)
            themeName.text = theme.getDisplayName(itemView.context)
            cardView.contentDescription = itemView.context.getString(
                R.string.page_theme_select_content_description,
                theme.getDisplayName(itemView.context)
            )

            val isSelected = theme.storageKey == currentTheme.storageKey
            selectionBorder.visibility = if (isSelected) View.VISIBLE else View.GONE

            cardView.setOnClickListener {
                onThemeClicked(theme, quranSettings)
            }
        }
    }

    companion object {
        const val TAG = "PageThemeBottomSheet"
    }
}
