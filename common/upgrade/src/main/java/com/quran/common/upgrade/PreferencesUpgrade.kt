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
   * Note that, for the case of async work, this method may choose to
   * return false, but then should write the version update to being to
   * after async work succeeds.
   *
   * @return a boolean as to whether to write the updated version or not.
   */
  fun upgrade(context: Context, from: Int, to: Int): Boolean
}
