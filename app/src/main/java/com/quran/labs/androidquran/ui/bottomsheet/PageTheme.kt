package com.quran.labs.androidquran.ui.bottomsheet

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants

sealed class PageTheme(val storageKey: String) {
    abstract fun getDisplayName(context: Context): String
    @ColorRes abstract fun getBackgroundColorRes(): Int
    @ColorRes abstract fun getTextColorRes(): Int
    abstract val isDarkTheme: Boolean

    @ColorInt
    fun getBackgroundColor(context: Context): Int =
        ContextCompat.getColor(context, getBackgroundColorRes())

    @ColorInt
    fun getPreviewTextColor(context: Context): Int =
        ContextCompat.getColor(context, getTextColorRes())

    object Paper : PageTheme(Constants.PAGE_THEME_PAPER) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_paper)
        override fun getBackgroundColorRes(): Int = R.color.theme_paper_bg
        override fun getTextColorRes(): Int = R.color.theme_paper_text
        override val isDarkTheme = false
    }

    object Original : PageTheme(Constants.PAGE_THEME_ORIGINAL) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_original)
        override fun getBackgroundColorRes(): Int = R.color.theme_original_bg
        override fun getTextColorRes(): Int = R.color.theme_original_text
        override val isDarkTheme = false
    }

    object Calm : PageTheme(Constants.PAGE_THEME_CALM) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_calm)
        override fun getBackgroundColorRes(): Int = R.color.theme_calm_bg
        override fun getTextColorRes(): Int = R.color.theme_calm_text
        override val isDarkTheme = false
    }

    object Focus : PageTheme(Constants.PAGE_THEME_FOCUS) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_focus)
        override fun getBackgroundColorRes(): Int = R.color.theme_focus_bg
        override fun getTextColorRes(): Int = R.color.theme_focus_text
        override val isDarkTheme = false
    }

    object Quiet : PageTheme(Constants.PAGE_THEME_QUIET) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_quiet)
        override fun getBackgroundColorRes(): Int = R.color.theme_quiet_bg
        override fun getTextColorRes(): Int = R.color.theme_quiet_text
        override val isDarkTheme = true
    }

    companion object {
        fun getAllThemes(): List<PageTheme> = listOf(Paper, Original, Quiet, Calm, Focus)

        fun fromKey(key: String): PageTheme = when (key) {
            Constants.PAGE_THEME_PAPER -> Paper
            Constants.PAGE_THEME_ORIGINAL -> Original
            Constants.PAGE_THEME_CALM -> Calm
            Constants.PAGE_THEME_FOCUS -> Focus
            Constants.PAGE_THEME_QUIET -> Quiet
            else -> Original
        }

        @JvmStatic
        fun isDarkTheme(themeKey: String): Boolean = fromKey(themeKey).isDarkTheme
    }
}
