package com.quran.labs.androidquran.util

import android.content.Context
import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class QuranPartialPageChecker @Inject constructor(val appContext: Context,
                                                  val quranSettings: QuranSettings,
                                                  val quranFileUtils: QuranFileUtils) {

  /**
   * Checks all the pages to find and delete partially downloaded images.
   */
  fun checkPages(pagesVersion: Int, numberOfPages: Int, width: String, secondWidth: String) {
    // internally, this is a set of page types to ensure running this
    // once per page type (even if there are multiple switches).
    if (!quranSettings.didCheckPartialImages()) {
      // avoid checking if we can avoid it
      if (canSkipPartialImageCheck(width, pagesVersion)) {
        quranSettings.setCheckedPartialImages()
      } else {
        try {
          // check the partial images for the width
          checkPartialImages(width, numberOfPages)
          if (width != secondWidth) {
            // and only check for the tablet dimension width if it's different
            checkPartialImages(secondWidth, numberOfPages)
          }
          quranSettings.setCheckedPartialImages()
        } catch (throwable: Throwable) {
          Timber.e(throwable, "Error while checking partial pages: $width and $secondWidth")
        }
      }
    }
  }

  /**
   * Check if we can skip the partial image check
   *
   * If we can be reasonably confident that the images were downloaded as a zip, we can skip
   * the partial image check.
   *
   * When a zip for images is downloaded, there's a version file included (.v5, for example).
   * This method checks for the existence of such a file.
   *
   * This check won't always be valid (at the time of this writing, this check is only valid
   * for madani (old and new) and qaloon images. It won't work for naskh and warsh, which
   * currently do not bundle a version file, though this is subject to change).
   */
  private fun canSkipPartialImageCheck(width: String, pagesVersion: Int): Boolean {
    // version files (.vX files) are only bundled with zips, so check
    // if any of them exist.
    for (i in pagesVersion downTo 1) {
      if (quranFileUtils.hasVersionFile(appContext, width, i)) {
        return true
      }
    }
    return false
  }

  /**
   * Check for partial images and delete them.
   * This opens every downloaded image and looks at the last set of pixels.
   * If the last few rows are blank, the image is assumed to be partial and
   * the image is deleted.
   */
  private fun checkPartialImages(width: String, numberOfPages: Int) {
    quranFileUtils.getQuranImagesDirectory(appContext, width)?.let { directoryName ->
      // scale images down to 1/16th of size
      val options = BitmapFactory.Options().apply {
        inSampleSize = 16
      }

      val directory = File(directoryName)
      // optimization to avoid re-generating the pixel array every time
      var pixelArray: IntArray? = null

      // skip pages 1 and 2 since they're "special" (not full pages)
      for (page in 3..numberOfPages) {
        val filename = quranFileUtils.getPageFileName(page)
        if (File(directory, filename).exists()) {
          val bitmap = BitmapFactory.decodeFile(
              directoryName + File.separator + filename, options)

          // this is an optimization to avoid allocating 8 * width of memory
          // for everything.
          val rowsToCheck =
              // madani, 8 for 1920, 6 for 1280, 4 or less for smaller
              // for naskh, 1 for everything
              // for qaloon, 2 for largest size, 1 for smallest
              // for warsh, 2 for everything
              when (width) {
                "_1920" -> 8
                "_1280" -> 6
                else -> 4
              }

          val bitmapWidth = bitmap.width
          // these should all be the same size, so we can just allocate once
          val pixels = if (pixelArray?.size == (bitmapWidth * rowsToCheck)) {
            pixelArray
          } else {
            pixelArray = IntArray(bitmapWidth * rowsToCheck)
            pixelArray
          }

          // get the set of pixels
          bitmap.getPixels(pixels,
              0,
              bitmapWidth,
              0,
              bitmap.height - rowsToCheck,
              bitmapWidth,
              rowsToCheck)

          // see if there's any non-0 pixel
          var foundPixel = false
          for (pixel in pixels) {
            if (pixel != 0) {
              // valid image, go on to the next one
              foundPixel = true
              break
            }
          }

          // if all are non-zero, assume the image is partially blank
          if (!foundPixel) {
            File(directory, filename).delete()
          }
        }
      }
    }
  }
}
