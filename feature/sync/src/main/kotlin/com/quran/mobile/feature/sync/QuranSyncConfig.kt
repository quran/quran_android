package com.quran.mobile.feature.sync

import android.content.Context
import com.quran.shared.pipeline.AppEnvironment
import com.quran.shared.pipeline.defaultAppEnvironment

/**
 * Reads build-provided sync configuration for the current Android variant.
 */
internal class QuranSyncConfig(
  private val context: Context
) {
  val clientId: String
    get() = syncString(R.string.quran_sync_oauth_client_id)

  val redirectUri: String
    get() = "${syncString(R.string.quran_sync_oauth_redirect_scheme)}://$REDIRECT_HOST"

  val isConfigured: Boolean
    get() = clientId.isNotBlank()

  val appEnvironment: AppEnvironment
    get() {
      val explicitEnvironment = syncString(R.string.quran_sync_environment).lowercase()
      if (explicitEnvironment in preliveEnvironmentNames) {
        return AppEnvironment.PRELIVE
      }
      if (explicitEnvironment in productionEnvironmentNames) {
        return AppEnvironment.PRODUCTION
      }

      val issuer = syncString(R.string.quran_sync_oauth_issuer_url).lowercase()
      return if (preliveEnvironmentNames.any { issuer.contains(it) }) {
        AppEnvironment.PRELIVE
      } else {
        defaultAppEnvironment()
      }
    }

  private val preliveEnvironmentNames = setOf("prelive", "preprod", "staging", "stage", "dev")
  private val productionEnvironmentNames = setOf("prod", "production")

  private fun syncString(resourceId: Int): String {
    return context.getString(resourceId).trim()
  }

  private companion object {
    const val REDIRECT_HOST = "callback"
  }
}
