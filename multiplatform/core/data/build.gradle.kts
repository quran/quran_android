plugins { kotlin("multiplatform"); id("com.android.library") }
kotlin {
 androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
 jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
 iosArm64()
 iosSimulatorArm64()
 sourceSets {
  commonMain.dependencies {
   implementation(project(":core:model"))
   implementation(project(":core:domain"))
   implementation(libs.ktor.client.core)
   implementation(libs.kotlinx.serialization.json)
  }
  androidMain.dependencies {
   implementation("androidx.media3:media3-exoplayer:1.8.0")
   implementation("androidx.media3:media3-session:1.8.0")
   implementation(libs.ktor.client.cio)
  }
  iosMain.dependencies { implementation(libs.ktor.client.darwin) }
  jvmMain.dependencies { implementation(libs.ktor.client.cio) }
  commonTest.dependencies {
   implementation(kotlin("test"))
   implementation(libs.kotlinx.coroutines.test)
  }
 }
}
android {
 namespace = "org.quran.app.core.data"
 compileSdk = 36
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 defaultConfig { minSdk = 26 }
}
