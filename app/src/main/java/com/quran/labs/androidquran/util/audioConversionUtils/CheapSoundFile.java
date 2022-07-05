/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.quran.labs.androidquran.util.audioConversionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
/**
 * CheapSoundFile is the parent class of several subclasses that each
 * do a "cheap" scan of various sound file formats, parsing as little
 * as possible in order to understand the high-level frame structure
 * and get a rough estimate of the volume level of each frame.  Each
 * subclass is able to:
 *  - open a sound file
 *  - return the sample rate and number of frames
 *  - return an approximation of the volume level of each frame
 *  - write a new sound file with a subset of the frames
 *
 * A frame should represent no less than 1 ms and no more than 100 ms of
 * audio.  This is compatible with the native frame sizes of most audio
 * file formats already, but if not, this class should expose virtual
 * frames in that size range.
 */
public class CheapSoundFile {
  public interface ProgressListener {
    /**
     * Will be called by the CheapSoundFile subclass periodically
     * with values between 0.0 and 1.0.  Return true to continue
     * loading the file, and false to cancel.
     */
    boolean reportProgress(double fractionComplete);
  }

  public interface Factory {
    public CheapSoundFile create();
    public String[] getSupportedExtensions();
  }

  static Factory[] sSubclassFactories = new Factory[] {
      CheapMP3.getFactory()
  };

  static ArrayList<String> sSupportedExtensions = new ArrayList<String>();
  static HashMap<String, Factory> sExtensionMap =
      new HashMap<String, Factory>();

  static {
    for (Factory f : sSubclassFactories) {
      for (String extension : f.getSupportedExtensions()) {
        sSupportedExtensions.add(extension);
        sExtensionMap.put(extension, f);
      }
    }
  }

  /**
   * Static method to create the appropriate CheapSoundFile subclass
   * given a filename.
   *
   * TODO: make this more modular rather than hardcoding the logic
   */
  public static CheapSoundFile create(String fileName,
                                      ProgressListener progressListener)
      throws java.io.FileNotFoundException,
      java.io.IOException {
    File f = new File(fileName);
    if (!f.exists()) {
      throw new java.io.FileNotFoundException(fileName);
    }
    String name = f.getName().toLowerCase();
    String[] components = name.split("\\.");
    if (components.length < 2) {
      return null;
    }
    Factory factory = sExtensionMap.get(components[components.length - 1]);
    if (factory == null) {
      return null;
    }
    CheapSoundFile soundFile = factory.create();
    soundFile.setProgressListener(progressListener);
    soundFile.ReadFile(f);
    return soundFile;
  }

  public static boolean isFilenameSupported(String filename) {
    String[] components = filename.toLowerCase().split("\\.");
    if (components.length < 2) {
      return false;
    }
    return sExtensionMap.containsKey(components[components.length - 1]);
  }

  /**
   * Return the filename extensions that are recognized by one of
   * our subclasses.
   */
  public static String[] getSupportedExtensions() {
    return sSupportedExtensions.toArray(
        new String[sSupportedExtensions.size()]);
  }

  protected ProgressListener mProgressListener = null;
  protected File mInputFile = null;

  protected CheapSoundFile() {
  }

  public void ReadFile(File inputFile)
      throws java.io.FileNotFoundException,
      java.io.IOException {
    mInputFile = inputFile;
  }

  public void setProgressListener(ProgressListener progressListener) {
    mProgressListener = progressListener;
  }

  public int getNumFrames() {
    return 0;
  }

  public int getSamplesPerFrame() {
    return 0;
  }

  public int[] getFrameOffsets() {
    return null;
  }

  public int[] getFrameLens() {
    return null;
  }

  public int[] getFrameGains() {
    return null;
  }

  public int getFileSizeBytes() {
    return 0;
  }

  public int getAvgBitrateKbps() {
    return 0;
  }

  public int getSampleRate() {
    return 0;
  }

  public int getChannels() {
    return 0;
  }

  public String getFiletype() {
    return "Unknown";
  }

  /**
   * If and only if this particular file format supports seeking
   * directly into the middle of the file without reading the rest of
   * the header, this returns the byte offset of the given frame,
   * otherwise returns -1.
   */
  public int getSeekableFrameOffset(int frame) {
    return -1;
  }

  private static final char[] HEX_CHARS = {
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
  public static String bytesToHex (byte hash[]) {
    char buf[] = new char[hash.length * 2];
    for (int i = 0, x = 0; i < hash.length; i++) {
      buf[x++] = HEX_CHARS[(hash[i] >>> 4) & 0xf];
      buf[x++] = HEX_CHARS[hash[i] & 0xf];
    }
    return new String(buf);
  }

  public String computeMd5OfFirst10Frames()
      throws java.io.FileNotFoundException,
      java.io.IOException,
      java.security.NoSuchAlgorithmException {
    int[] frameOffsets = getFrameOffsets();
    int[] frameLens = getFrameLens();
    int numFrames = frameLens.length;
    if (numFrames > 10) {
      numFrames = 10;
    }

    MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
    FileInputStream in = new FileInputStream(mInputFile);
    int pos = 0;
    for (int i = 0; i < numFrames; i++) {
      int skip = frameOffsets[i] - pos;
      int len = frameLens[i];
      if (skip > 0) {
        in.skip(skip);
        pos += skip;
      }
      byte[] buffer = new byte[len];
      in.read(buffer, 0, len);
      digest.update(buffer);
      pos += len;
    }
    in.close();
    byte[] hash = digest.digest();
    return bytesToHex(hash);
  }

  public void WriteFile(File outputFile, int startFrame, int numFrames)
      throws java.io.IOException {
  }
}
