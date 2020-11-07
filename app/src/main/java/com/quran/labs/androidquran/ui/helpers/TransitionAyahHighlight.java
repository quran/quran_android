package com.quran.labs.androidquran.ui.helpers;

public class TransitionAyahHighlight extends AyahHighlight {
  final private static String ARROW = "->";
  private AyahHighlight source, destination;

  public TransitionAyahHighlight(AyahHighlight source, AyahHighlight destination) {
    super(source.getKey() + ARROW + destination.getKey(), true);
    this.source = source;
    this.destination = destination;
  }

  public AyahHighlight getSource() {
    return source;
  }

  public AyahHighlight getDestination() {
    return destination;
  }
}
