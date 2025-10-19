plugins {
  // only Android because of the build version check
  id("quran.android.library.android")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.common.networking"

dependencies {
  implementation(project(":common:data"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.dnsjava)
  implementation(libs.okhttp.dnsoverhttps)
  api(libs.okhttp.tls)

  implementation(libs.timber)
}
