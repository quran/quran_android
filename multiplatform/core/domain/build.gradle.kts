plugins { kotlin("multiplatform"); id("com.android.library") }
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        commonMain.dependencies { api(project(":core:model")) }
    }
}
android { namespace = "org.quran.app.domain"; compileSdk = 36; defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
