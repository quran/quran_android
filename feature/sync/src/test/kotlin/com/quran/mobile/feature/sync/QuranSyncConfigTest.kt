package com.quran.mobile.feature.sync

import com.google.common.truth.Truth.assertThat
import com.quran.shared.pipeline.AppEnvironment
import com.quran.shared.pipeline.defaultAppEnvironment
import org.junit.Test

class QuranSyncConfigTest {

  @Test
  fun `resolveAppEnvironment matches prelive host labels`() {
    val preliveIssuers = listOf(
      "https://prelive.quran.com",
      "https://prelive-oauth2.quran.foundation",
      "https://dev.quran.com/oauth",
      "https://staging-auth.quran.foundation:8443/oauth2",
      "HTTPS://PREPROD.QURAN.COM"
    )

    preliveIssuers.forEach { issuer ->
      assertThat(QuranSyncConfig.resolveAppEnvironment(explicitEnvironment = "", issuer = issuer))
        .isEqualTo(AppEnvironment.PRELIVE)
    }
  }

  @Test
  fun `resolveAppEnvironment does not match dev substring inside non-env host or path`() {
    val nonPreliveIssuers = listOf(
      "https://developer.quran.com",
      "https://oauth2.quran.foundation/dev",
      "https://auth.quran.com/prelive",
      "https://mydevsite.example.com",
      "https://oauth2.quran.foundation"
    )

    nonPreliveIssuers.forEach { issuer ->
      assertThat(QuranSyncConfig.resolveAppEnvironment(explicitEnvironment = "", issuer = issuer))
        .isEqualTo(defaultAppEnvironment())
    }
  }

  @Test
  fun `resolveAppEnvironment respects explicit environment over issuer`() {
    assertThat(
      QuranSyncConfig.resolveAppEnvironment(
        explicitEnvironment = "prod",
        issuer = "https://prelive.quran.com"
      )
    ).isEqualTo(AppEnvironment.PRODUCTION)

    assertThat(
      QuranSyncConfig.resolveAppEnvironment(
        explicitEnvironment = "prelive",
        issuer = "https://oauth2.quran.foundation"
      )
    ).isEqualTo(AppEnvironment.PRELIVE)
  }
}
