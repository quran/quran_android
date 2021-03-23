package com.quran.common.upgrade

import android.content.Context

/**
 * Upgrade preferences between one version of the app and another.
 */
fun interface PreferencesUpgrade {

  /**
   * Upgrades from one given version to another. [to] is expected to be
   * the current version code of the app.
   *
   * @return a boolean as to whether the upgrade succeeded or not.
   */
  fun upgrade(context: Context, from: Int, to: Int): Boolean
}
