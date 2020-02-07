package com.quran.labs.androidquran.util

import android.graphics.BitmapFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class QuranPartialPageChecker @Inject constructor() {

  /**
   * Checks all the pages to find partially downloaded images.
   */
  fun checkPages(directory: String, numberOfPages: Int, width: String): List<Int> {
    // past versions of the partial page checker didn't run the checker
    // whenever any .vX file exists. this was noted as a "works sometimes"
    // solution because not all zip files contain .vX files (ex naskh and
    // warsh). removed this now because it is actually invalid for madani
    // in some cases as well due to the fact that someone could have manually
    // downloaded images, have broken images, and then get a patch zip which
    // contains the .vX file.
    try {
      // check the partial images for the width
      return checkPartialImages(directory, width, numberOfPages)
    } catch (throwable: Throwable) {
      Timber.e(throwable, "Error while checking partial pages: $width")
    }
    return emptyList()
  }

  /**
   * Check for partial images and return them.
   * This opens every downloaded image and looks at the last set of pixels.
   * If the last few rows are blank, the image is assumed to be partial and
   * the image is returned.
   */
  private fun checkPartialImages(directoryName: String,
                                 width: String,
                                 numberOfPages: Int): List<Int> {
    val result = mutableListOf<Int>()

    // scale images down to 1/16th of size
    val options = BitmapFactory.Options()
        .apply {
          inSampleSize = 16
        }

    val directory = File(directoryName)
    // optimization to avoid re-generating the pixel array every time
    var pixelArray: IntArray? = null

    // skip pages 1 and 2 since they're "special" (not full pages)
    for (page in 3..numberOfPages) {
      val filename = QuranFileUtils.getPageFileName(page)
      if (File(directory, filename).exists()) {
        val bitmap = BitmapFactory.decodeFile(
            directoryName + File.separator + filename, options
        )

        // this is an optimization to avoid allocating 8 * width of memory
        // for everything.
        val rowsToCheck =
          // madani, 9 for 1920, 7 for 1280, 5 for 1260 and 1024, and
          //   less for smaller images.
          // for naskh, 1 for everything
          // for qaloon, 2 for largest size, 1 for smallest
          // for warsh, 2 for everything
          when (width) {
            "_1024", "_1260" -> 5
            "_1280" -> 7
            "_1920" -> 9
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
        bitmap.getPixels(
            pixels,
            0,
            bitmapWidth,
            0,
            bitmap.height - rowsToCheck,
            bitmapWidth,
            rowsToCheck
        )

        // see if there's any non-0 pixel
        val foundPixel = pixels.any { it != 0 }

        // if all are non-zero, assume the image is partially blank
        if (!foundPixel) {
          result.add(page)
        }
      }
    }
    return result
  }
}
