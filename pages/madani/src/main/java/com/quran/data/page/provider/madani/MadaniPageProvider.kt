package com.quran.data.page.provider.madani

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.madani.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.R as audioR

class MadaniPageProvider : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 8

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = ""

  override fun getPreviewTitle() = R.string.madani_title

  override fun getPreviewDescription() = R.string.madani_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris(): List<Qari> {
    return listOf(
      Qari(
        0,
        audioR.string.qari_minshawi_murattal_gapless,
        url = "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
        path = "minshawi_murattal",
        hasGaplessAlternative = false,
        db = "minshawi_murattal"
      ),
      Qari(
        1,
        audioR.string.qari_husary_gapless,
        url = "https://download.quranicaudio.com/quran/mahmood_khaleel_al-husaree/",
        path = "husary",
        hasGaplessAlternative = false,
        db = "husary"
      ),
      Qari(
        2,
        audioR.string.qari_basfar,
        url = "https://mirrors.quranicaudio.com/everyayah/Abdullah_Basfar_192kbps/",
        path = "2",
        hasGaplessAlternative = false,
        db = null
      )
    )
  }

  companion object {
    private const val baseUrl = "https://android.quran.com/data"
    private val dataSource by lazy { MadaniDataSource() }
  }
}
