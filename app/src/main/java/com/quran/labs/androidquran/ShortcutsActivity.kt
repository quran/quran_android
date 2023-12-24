package com.quran.labs.androidquran

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity

/**
 * Handle shortcuts by lauching the appropriate activity.
 * Currently, there is one shortcut to go to the last page,
 * and one shortcut to jump to any location in the Quran.
 */
class ShortcutsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val action = intent.action
    val intentToLaunch: Intent
    val shortcutSelected: String
    when (action) {
        ACTION_JUMP_TO_LATEST -> {
          intentToLaunch = Intent(this, QuranActivity::class.java)
          intentToLaunch.action = action
          shortcutSelected = JUMP_TO_LATEST_SHORTCUT_NAME
        }
        ACTION_JUMP_TO -> {
          intentToLaunch = Intent(this, ShowJumpFragmentActivity::class.java)
          shortcutSelected = JUMP_TO_SHORTCUT_NAME
        }
      else -> {
        finish()
        return
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      recordShortcutUsage(shortcutSelected)
    }
    finish()
    startActivity(intentToLaunch)
  }

  @TargetApi(Build.VERSION_CODES.N_MR1)
  private fun recordShortcutUsage(shortcut: String) {
    val shortcutManager = getSystemService(ShortcutManager::class.java)
    shortcutManager?.reportShortcutUsed(shortcut)
  }

  companion object {
    const val ACTION_JUMP_TO_LATEST = "com.quran.labs.androidquran.last_page"
    const val ACTION_JUMP_TO = "com.quran.labs.androidquran.jump_to"
    private const val JUMP_TO_LATEST_SHORTCUT_NAME = "lastPage"
    private const val JUMP_TO_SHORTCUT_NAME = "jumpTo"
  }
}
