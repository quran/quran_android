package com.quran.common.networking

import android.os.Build
import com.quran.common.networking.dns.DnsModule
import com.quran.data.di.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

@BindingContainer(includes = [DnsModule::class])
object NetworkModule {
  private const val DEFAULT_READ_TIMEOUT_SECONDS = 20
  private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 20
  private const val DEFAULT_OKHTTP_CLIENT = "DEFAULT_OKHTTP_CLIENT"
  private const val LEGACY_OKHTTP_CLIENT = "LEGACY_OKHTTP_CLIENT"

  @Provides
  @SingleIn(AppScope::class)
  fun provideOkHttpClient(
    @Named(DEFAULT_OKHTTP_CLIENT) okHttpClientProvider: Provider<OkHttpClient>,
    @Named(LEGACY_OKHTTP_CLIENT) legacyOkHttpClientProvider: Provider<OkHttpClient>
  ): OkHttpClient {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      okHttpClientProvider()
    } else {
      legacyOkHttpClientProvider()
    }
  }

  @Provides
  @Named(DEFAULT_OKHTTP_CLIENT)
  @SingleIn(AppScope::class)
  fun provideDefaultOkHttpClient(dns: Dns): OkHttpClient {
    return OkHttpClient.Builder()
      .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .dns(dns)
      .build()
  }

  @Provides
  @Named(LEGACY_OKHTTP_CLIENT)
  @SingleIn(AppScope::class)
  fun provideLegacyOkHttpClient(dns: Dns, certificates: HandshakeCertificates): OkHttpClient {
    // https://stackoverflow.com/questions/64844311
    return OkHttpClient.Builder()
      .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
      .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .dns(dns)
      .build()
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideHandshakeCertificates(): HandshakeCertificates {
    // https://stackoverflow.com/questions/64844311
    val certificate = readCertificate()
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val isgCertificate = certificateFactory.generateCertificate(ByteArrayInputStream(certificate.toByteArray(Charsets.UTF_8)))

    return HandshakeCertificates.Builder()
      .addTrustedCertificate(isgCertificate as X509Certificate)
      .addPlatformTrustedCertificates()
      .build()
  }

  private fun readCertificate(): String {
    // isgrootx1.pem from https://letsencrypt.org/certs/isrgrootx1.pem
    return """
      -----BEGIN CERTIFICATE-----
      MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
      TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
      cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
      WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
      ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
      MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
      h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
      0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
      A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
      T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
      B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
      B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
      KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
      OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
      jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
      qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
      rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
      HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
      hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
      ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
      3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
      NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
      ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
      TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
      jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
      oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
      4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
      mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
      emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
      -----END CERTIFICATE-----
    """.trimIndent()
  }
}
