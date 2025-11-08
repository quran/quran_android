plugins {
  id("quran.android.library.android")
  id("app.cash.sqldelight")
  alias(libs.plugins.metro)
}

sqldelight {
  databases {
    create("LineByLineAyahInfoDatabase") {
      packageName.set("com.quran.mobile.linebyline.data")
      schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
      verifyMigrations.set(true)
    }
  }
}

android.namespace = "com.quran.mobile.linebyline.data"

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  implementation(libs.kotlinx.collections.immutable)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
}
