package com.quran.labs.androidquran.ui.bottomsheet

import android.content.Context
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants

sealed class PageTheme(val storageKey: String) {
    abstract fun getDisplayName(context: Context): String
    abstract fun getBackgroundColor(context: Context): Int
    abstract fun getPreviewTextColor(context: Context): Int
    abstract val isDarkTheme: Boolean

    object Paper : PageTheme(Constants.PAGE_THEME_PAPER) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_paper)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_paper)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_dark)
        override val isDarkTheme = false
    }

    object Original : PageTheme(Constants.PAGE_THEME_ORIGINAL) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_original)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_original)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_dark)
        override val isDarkTheme = false
    }

    object Calm : PageTheme(Constants.PAGE_THEME_CALM) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_calm)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_calm)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_dark)
        override val isDarkTheme = false
    }

    object Focus : PageTheme(Constants.PAGE_THEME_FOCUS) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_focus)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_focus)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_dark)
        override val isDarkTheme = false
    }

    object Quiet : PageTheme(Constants.PAGE_THEME_QUIET) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_quiet)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_quiet)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_light)
        override val isDarkTheme = true
    }

    object Black : PageTheme(Constants.PAGE_THEME_BLACK) {
        override fun getDisplayName(context: Context): String =
            context.getString(R.string.page_theme_black)
        override fun getBackgroundColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_black)
        override fun getPreviewTextColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.theme_text_light)
        override val isDarkTheme = true
    }

    companion object {
        fun getAllThemes(): List<PageTheme> = listOf(Paper, Original, Calm, Focus, Quiet, Black)

        fun fromKey(key: String): PageTheme = when (key) {
            Constants.PAGE_THEME_PAPER -> Paper
            Constants.PAGE_THEME_ORIGINAL -> Original
            Constants.PAGE_THEME_CALM -> Calm
            Constants.PAGE_THEME_FOCUS -> Focus
            Constants.PAGE_THEME_QUIET -> Quiet
            Constants.PAGE_THEME_BLACK -> Black
            else -> Original
        }

        @JvmStatic
        fun isDarkTheme(themeKey: String): Boolean = fromKey(themeKey).isDarkTheme
    }
}
