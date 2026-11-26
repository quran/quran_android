package com.quran.mobile.feature.sync

import android.content.Context
import com.quran.shared.pipeline.AppEnvironment
import com.quran.shared.pipeline.defaultAppEnvironment
import java.net.URI

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
    get() = resolveAppEnvironment(
      explicitEnvironment = syncString(R.string.quran_sync_environment),
      issuer = syncString(R.string.quran_sync_oauth_issuer_url)
    )

  private fun syncString(resourceId: Int): String {
    return context.getString(resourceId).orEmpty().trim()
  }

  internal companion object {
    const val REDIRECT_HOST = "callback"
    private val preliveEnvironmentNames = setOf("prelive", "preprod", "staging", "stage", "dev")
    private val productionEnvironmentNames = setOf("prod", "production")

    internal fun resolveAppEnvironment(
      explicitEnvironment: String,
      issuer: String
    ): AppEnvironment {
      val normalizedExplicit = explicitEnvironment.trim().lowercase()
      if (normalizedExplicit in preliveEnvironmentNames) {
        return AppEnvironment.PRELIVE
      }
      if (normalizedExplicit in productionEnvironmentNames) {
        return AppEnvironment.PRODUCTION
      }

      return if (isPreliveIssuer(issuer)) {
        AppEnvironment.PRELIVE
      } else {
        defaultAppEnvironment()
      }
    }

    private fun isPreliveIssuer(issuer: String): Boolean {
      val trimmed = issuer.trim()
      if (trimmed.isEmpty()) return false
      val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return false
      val labels = host.split('.', '-')
      return labels.any { it in preliveEnvironmentNames }
    }
  }
}
