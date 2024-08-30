package com.quran.labs.androidquran.util

import android.text.TextUtils
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.data.Constants
import dagger.Reusable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

@Reusable
class QuranAppUtils @Inject internal constructor(private val quranInfo: QuranInfo) {
  fun getQuranAppUrlObservable(
    key: String,
    start: SuraAyah,
    end: SuraAyah
  ): Single<String> {
    return Single.fromCallable {
      val sura = start.sura
      val startAyah = start.ayah
      // quranapp only supports sharing within a sura
      val endAyah = if (end.sura == start.sura) end.ayah else quranInfo.getNumberOfAyahs(start.sura)
      getQuranAppUrl(key, sura, startAyah, endAyah)
    }.subscribeOn(Schedulers.io())
  }

  private fun getQuranAppUrl(key: String, sura: Int, startAyah: Int?, endAyah: Int?): String {
    val fallbackUrl = Constants.QURAN_APP_BASE + sura
    return try {
      val params = buildMap {
        put("surah", sura.toString())
        if (startAyah != null) {
          put("start_ayah", startAyah.toString())
          if (endAyah != null) {
            put("end_ayah", endAyah.toString())
          } else {
            put("end_ayah", startAyah.toString())
          }
        }
        put("key", key)
      }
      val result = getQuranAppUrl(params)
      val url = if (!TextUtils.isEmpty(result)) {
        val json = JSONObject(result)
        json.getString("url")
      } else {
        ""
      }

      return url.ifEmpty { fallbackUrl }
    } catch (e: Exception) {
      Timber.d(e, "error getting QuranApp url")
      fallbackUrl
    }
  }

  companion object {
    @Throws(IOException::class)
    private fun getQuranAppUrl(params: Map<String, String>): String {
      val url = try {
        URL(Constants.QURAN_APP_ENDPOINT)
      } catch (me: MalformedURLException) {
        // ignore
        null
      }

      val builder = StringBuilder()
      val iterator = params.entries.iterator()
      while (iterator.hasNext()) {
        val item = iterator.next()
        builder.append(item.key).append("=").append(item.value)
        if (iterator.hasNext()) {
          builder.append('&')
        }
      }

      var result = ""
      val body = builder.toString()
      val bytes = body.toByteArray(charset("UTF-8"))
      var conn: HttpURLConnection? = null
      try {
        // TODO: use OkHttp
        conn = (url!!.openConnection() as HttpURLConnection).apply {
          readTimeout = 10000
          connectTimeout = 15000
          doOutput = true
          doInput = true
          useCaches = false
          setFixedLengthStreamingMode(bytes.size)
          requestMethod = "POST"
          setRequestProperty(
            "Content-Type",
            "application/x-www-form-urlencoded;charset=UTF-8"
          )
        }

        // post the request
        val out = conn.outputStream
        out.write(bytes)
        out.close()

        // handle the response
        val reader =
          BufferedReader(
            InputStreamReader(
              conn.inputStream, "UTF-8"
            )
          )

        var line: String
        while ((reader.readLine().also { line = it }) != null) {
          result += line
        }

        try {
          reader.close()
        } catch (e: Exception) {
          // ignore
        }
      } finally {
        conn?.disconnect()
      }

      return result
    }
  }
}
