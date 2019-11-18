package com.quran.labs.androidquran.util

import com.quran.labs.androidquran.extension.closeQuietly
import io.reactivex.Maybe
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import okio.Source
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import javax.inject.Inject

class ImageUtil @Inject constructor(private val okHttpClient: OkHttpClient) {

  fun downloadImage(url: String, outputPath: File): Maybe<File> {
    return Maybe.fromCallable {
      if (!outputPath.exists()) {
        val destination = File(outputPath.path + ".tmp")
        val request = Request.Builder()
            .url(url)
            .build()
        val call = okHttpClient.newCall(request)
        val response = call.execute()

        var source: Source? = null
        val sink = destination.sink().buffer()
        try {
          if (response.isSuccessful) {
            source = response.body()?.source()
            if (source != null) {
              sink.writeAll(source)
            }
            destination.copyTo(outputPath)
            destination.delete()
          }
        } catch (exception: IOException) {
          // ignore - socket was likely closed, etc.
        } finally {
          sink.closeQuietly()
          source.closeQuietly()
        }
      }
      outputPath
    }
  }
}
