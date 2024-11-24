plugins {
  // only Android because of the build version check
  id("quran.android.library.android")
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.common.networking"

dependencies {
  implementation(project(":common:data"))
  implementation(libs.dagger.runtime)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.dnsjava)
  implementation(libs.okhttp.dnsoverhttps)
  api(libs.okhttp.tls)
}
