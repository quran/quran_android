plugins {
  id("quran.android.library.android")
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.anvil)
}

anvil {
  useKsp(contributesAndFactoryGeneration = true)
  generateDaggerFactories.set(true)
}

android.namespace = "com.quran.mobile.translation"

sqldelight {
   databases {
      create("TranslationsDatabase") {
         packageName.set("com.quran.mobile.translation.data")
         schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
         verifyMigrations.set(true)
      }
   }
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))

  // dagger
  implementation(libs.dagger.runtime)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // sqldelight
  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines.extensions)
  implementation(libs.sqldelight.primitive.adapters)
}
