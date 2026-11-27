package com.quran.labs.androidquran.util

import android.content.Context
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class NetworkSecurityConfigTest {

  private var previousLibcorePolicy: Any? = null

  @Before
  fun setUp() {
    val libcoreClass = Class.forName("libcore.net.NetworkSecurityPolicy")
    previousLibcorePolicy = libcoreClass.getMethod("getInstance").invoke(null)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val providerClass = Class.forName("android.security.net.config.NetworkSecurityConfigProvider")
    providerClass.getMethod("install", Context::class.java).invoke(null, context)
  }

  @After
  fun tearDown() {
    Security.removeProvider("AndroidNSSP")
    previousLibcorePolicy?.let { policy ->
      val libcoreClass = Class.forName("libcore.net.NetworkSecurityPolicy")
      libcoreClass.getMethod("setInstance", libcoreClass).invoke(null, policy)
    }
  }

  @Test
  fun `cleartext traffic is disabled for quran domains and globally`() {
    val policy = NetworkSecurityPolicy.getInstance()
    assertThat(policy.isCleartextTrafficPermitted).isFalse()
    assertThat(policy.isCleartextTrafficPermitted("quran.com")).isFalse()
    assertThat(policy.isCleartextTrafficPermitted("api.quran.com")).isFalse()
    assertThat(policy.isCleartextTrafficPermitted("quranicaudio.com")).isFalse()
    assertThat(policy.isCleartextTrafficPermitted("download.quranicaudio.com")).isFalse()
  }
}
