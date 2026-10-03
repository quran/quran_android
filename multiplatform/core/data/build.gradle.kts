plugins { kotlin("multiplatform"); id("com.android.library") }
kotlin {
 androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }; iosArm64(); iosSimulatorArm64()
 sourceSets {
 commonMain.dependencies { implementation(project(":core:model"))
implementation(project(":core:domain")) }
 androidMain.dependencies { implementation("androidx.media3:media3-exoplayer:1.8.0"); implementation("androidx.media3:media3-session:1.8.0") }
 commonTest.dependencies { implementation(kotlin("test")) }
 }
}
android { namespace = "org.quran.app.core.data"; compileSdk = 36; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }; defaultConfig { minSdk = 26 } }
