package com.quran.labs.androidquran.ui.helpers

sealed class AyahHighlight(val key: String, val isTransition: Boolean) {

  override fun equals(other: Any?): Boolean {
    if (other === this) {
      return true
    }
    if (other == null || other.javaClass != this.javaClass) {
      return false
    }
    val ayahHighlight = other as AyahHighlight
    return key == ayahHighlight.key
  }

  override fun hashCode(): Int {
    return key.hashCode()
  }

  override fun toString(): String {
    return key
  }
}
