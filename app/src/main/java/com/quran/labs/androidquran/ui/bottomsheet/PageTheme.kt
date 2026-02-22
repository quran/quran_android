package com.quran.labs.androidquran.ui.bottomsheet

import android.content.Context
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import androidx.core.graphics.toColorInt

sealed class PageTheme(val storageKey: String) {
  abstract fun getDisplayName(context: Context): String
  abstract fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int

  object Auto : PageTheme(Constants.PAGE_THEME_AUTO) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_auto)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      if (isDarkMode) Constants.COLOR_THEME_AUTO_DARK.toColorInt()
      else Constants.COLOR_THEME_AUTO_LIGHT.toColorInt()
  }

  object Paper : PageTheme(Constants.PAGE_THEME_PAPER) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_paper)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      Constants.COLOR_THEME_PAPER.toColorInt()
  }

  object Original : PageTheme(Constants.PAGE_THEME_ORIGINAL) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_original)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      Constants.COLOR_THEME_ORIGINAL.toColorInt()
  }

  object Quiet : PageTheme(Constants.PAGE_THEME_QUIET) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_quiet)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      Constants.COLOR_THEME_QUIET.toColorInt()
  }

  object Calm : PageTheme(Constants.PAGE_THEME_CALM) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_calm)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      Constants.COLOR_THEME_CALM.toColorInt()
  }

  object Focus : PageTheme(Constants.PAGE_THEME_FOCUS) {
    override fun getDisplayName(context: Context): String =
      context.getString(R.string.page_theme_focus)

    override fun getBackgroundColor(context: Context, isDarkMode: Boolean): Int =
      Constants.COLOR_THEME_FOCUS.toColorInt()
  }

  companion object {
    fun getAllThemes(): List<PageTheme> = listOf(Auto, Paper, Original, Quiet, Calm, Focus)

    fun fromKey(key: String): PageTheme = when (key) {
      Constants.PAGE_THEME_PAPER -> Paper
      Constants.PAGE_THEME_ORIGINAL -> Original
      Constants.PAGE_THEME_QUIET -> Quiet
      Constants.PAGE_THEME_CALM -> Calm
      Constants.PAGE_THEME_FOCUS -> Focus
      else -> Auto
    }
  }
}
