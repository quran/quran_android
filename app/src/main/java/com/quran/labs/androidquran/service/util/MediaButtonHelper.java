/*
 * This code is based on the RandomMusicPlayer example from
 * the Android Open Source Project samples.  It has been modified
 * for use in Quran Android.
 */
package com.quran.labs.androidquran.service.util;

import android.content.ComponentName;
import android.media.AudioManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import timber.log.Timber;

/**
 * Class that assists with handling new media button APIs available in API level 8.
 */
public class MediaButtonHelper {

  static {
    initializeStaticCompatMethods();
  }

  static Method sMethodRegisterMediaButtonEventReceiver;
  static Method sMethodUnregisterMediaButtonEventReceiver;

  static void initializeStaticCompatMethods() {
    try {
      sMethodRegisterMediaButtonEventReceiver = AudioManager.class.getMethod(
          "registerMediaButtonEventReceiver",
          new Class[]{ComponentName.class});
      sMethodUnregisterMediaButtonEventReceiver = AudioManager.class.getMethod(
          "unregisterMediaButtonEventReceiver",
          new Class[]{ComponentName.class});
    } catch (NoSuchMethodException e) {
      // Silently fail when running on an OS before API level 8.
    }
  }

  public static void registerMediaButtonEventReceiverCompat(AudioManager audioManager,
      ComponentName receiver) {
    if (sMethodRegisterMediaButtonEventReceiver == null) {
      return;
    }

    try {
      sMethodRegisterMediaButtonEventReceiver.invoke(audioManager, receiver);
    } catch (InvocationTargetException e) {
      // Unpack original exception when possible
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        throw (Error) cause;
      } else {
        // Unexpected checked exception; wrap and re-throw
        throw new RuntimeException(e);
      }
    } catch (IllegalAccessException e) {
      Timber.e("IllegalAccessException invoking registerMediaButtonEventReceiver.");
      e.printStackTrace();
    }
  }

  public static void unregisterMediaButtonEventReceiverCompat(AudioManager audioManager,
      ComponentName receiver) {
    if (sMethodUnregisterMediaButtonEventReceiver == null) {
      return;
    }

    try {
      sMethodUnregisterMediaButtonEventReceiver.invoke(audioManager, receiver);
    } catch (InvocationTargetException e) {
      // Unpack original exception when possible
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      } else if (cause instanceof Error) {
        throw (Error) cause;
      } else {
        // Unexpected checked exception; wrap and re-throw
        throw new RuntimeException(e);
      }
    } catch (IllegalAccessException e) {
      Timber.e("IllegalAccessException invoking unregisterMediaButtonEventReceiver.");
      e.printStackTrace();
    }
  }
}
