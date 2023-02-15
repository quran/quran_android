package com.quran.labs.androidquran.util

import io.reactivex.rxjava3.core.Maybe
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject

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
          val response = call.execute()
          if (response.isSuccessful) {
            // save the png from the download to a temporary file
            response.body
              ?.source()
              ?.use { source ->
                destination.sink()
                  .buffer()
                  .use { destination ->
                    destination.writeAll(source)
                  }
              }

            // and write it to the normal file
            destination.source()
              .use { source ->
                outputPath.sink()
                  .buffer()
                  .use { destination ->
                    destination.writeAll(source)
                  }
              }

            // and delete the old one
            destination.delete()
          }
        } catch (ioException: IOException) {
          // if we're interrupted, pretend nothing happened. This happened
          // due to a dispose / cancellation. Maybe's fromCallable will not
          // actually emit in this case.
          //
          // a more proper fix would probably be to use Maybe.create and
          // set a cancellation handler to stop the network call.
        }
      }
      outputPath
    }
  }
}
