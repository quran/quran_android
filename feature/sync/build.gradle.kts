import java.io.FileInputStream
import java.util.Properties

plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.anvil)
}

android {
  namespace  = "com.quran.mobile.feature.sync"
  buildFeatures.buildConfig = true

  val properties = Properties()
  val propertiesFile = project.projectDir.resolve("oauth.properties")

  if (propertiesFile.exists()) {
    properties.load(FileInputStream(propertiesFile))
  }

  defaultConfig {
    buildConfigField(
      "String",
      "CLIENT_ID",
      "\"${properties.getProperty("client_id", "")}\""
    )
    buildConfigField(
      "String",
      "DISCOVERY_URI",
      "\"${properties.getProperty("discovery_uri", "")}\""
    )
    buildConfigField(
      "String",
      "SCOPES",
      "\"${properties.getProperty("scopes", "")}\""
    )
    buildConfigField(
      "String",
      "REDIRECT_URI",
      "\"${properties.getProperty("redirect_uri", "")}\""
    )
  }
}

anvil {
  useKsp(contributesAndFactoryGeneration = true, componentMerging = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  // androidx
  implementation(libs.androidx.appcompat)
  api(libs.androidx.datastore.prefs)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // app auth library
  implementation(libs.appauth)

  // dagger
  implementation(libs.dagger.runtime)

  // timber
  implementation(libs.timber)
}
