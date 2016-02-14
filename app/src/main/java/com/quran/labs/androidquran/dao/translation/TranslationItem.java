package com.quran.labs.androidquran.dao.translation;

public class TranslationItem implements TranslationRowData {
  public final int localVersion;
  public final Translation translation;

  public TranslationItem(Translation translation) {
    this(translation, 0);
  }

  public TranslationItem(Translation translation, int localVersion) {
    this.translation = translation;
    this.localVersion = localVersion;
  }

  public boolean exists() {
    return localVersion > 0;
  }

  @Override
  public String name() {
    return this.translation.displayName;
  }

  @Override
  public boolean isSeparator() {
    return false;
  }

  @Override
  public boolean needsUpgrade() {
    return localVersion > 0 && this.translation.currentVersion > this.localVersion;
  }

  public TranslationItem withTranslationRemoved() {
    return new TranslationItem(this.translation, 0);
  }

  public TranslationItem withTranslationVersion(int version) {
    return new TranslationItem(this.translation, version);
  }
}