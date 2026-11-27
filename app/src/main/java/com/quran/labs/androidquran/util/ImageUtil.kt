package com.quran.labs.androidquran.util

import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.core.Maybe
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException

class ImageUtil @Inject constructor(private val okHttpClient: OkHttpClient) {

  fun downloadImage(
    url: String,
    outputPath: File
  ): Maybe<File> {
    return Maybe.fromCallable {
      if (!outputPath.exists()) {
        val destination = File(outputPath.path + ".tmp")
        val request = Request.Builder()
          .url(url)
          .build()
        val call = okHttpClient.newCall(request)

        try {
          call.execute().use { response ->
            if (response.isSuccessful) {
              // save the png from the download to a temporary file
              response.body
                .source()
                .use { source ->
                  destination.sink()
                    .buffer()
                    .use { sink ->
                      sink.writeAll(source)
                    }
                }

              if (!destination.renameTo(outputPath)) {
                // fallback copy when atomic rename is unsupported
                destination.source()
                  .use { source ->
                    outputPath.sink()
                      .buffer()
                      .use { sink ->
                        sink.writeAll(source)
                      }
                  }
                destination.delete()
              }
            }
          }
        } catch (_: IOException) {
          destination.delete()
        } catch (_: InterruptedException) {
          destination.delete()
          // if we're interrupted, pretend nothing happened. This happened
          // due to a dispose / cancellation. Maybe's fromCallable will not
          // actually emit in this case.
        }
      }
      outputPath
    }
  }
}
