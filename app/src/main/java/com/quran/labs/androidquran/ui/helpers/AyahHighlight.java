package com.quran.labs.androidquran.ui.helpers;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

public class AyahHighlight {
  private String key;
  private boolean transition;

  public AyahHighlight(String key) {
    this.key = key;
    this.transition = false;
  }

  public AyahHighlight(String key, boolean transition) {
    this.key = key;
    this.transition = transition;
  }

  public String getKey() {
    return key;
  }

  public boolean isTransition() {
    return transition;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if((obj == null) || (obj.getClass() != this.getClass())) {
      return false;
    }

    AyahHighlight ayahHighlight = (AyahHighlight)obj;
    return this.key.equals(ayahHighlight.key);
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return key;
  }
}



