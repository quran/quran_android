package com.quran.mobile.translation.mapper

import com.quran.mobile.translation.model.LocalTranslation

object LocalTranslationMapper {

  val mapper: ((
    id: Long,
    name: String,
    translator: String?,
    translatorForeign: String?,
    filename: String,
    url: String,
    languageCode: String?,
    version: Long,
    minimumRequiredVersion: Long,
    userDisplayOrder: Long,
  ) -> LocalTranslation) =
    { id, name, translator, translatorForeign, filename, url, languageCode, version, minimumRequiredVersion, displayOrder ->
      LocalTranslation(
        id = id,
        name = name,
        translator = translator,
        translatorForeign = translatorForeign,
        filename = filename,
        url = url,
        languageCode = languageCode,
        version = version.toInt(),
        minimumVersion = minimumRequiredVersion.toInt(),
        displayOrder = displayOrder.toInt(),
      )
    }
}
