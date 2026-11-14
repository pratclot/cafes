plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.pratclot.cafes.recognition"
  compileSdk = 37
  defaultConfig { minSdk = 24 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:passthrough-android"))
  implementation(libs.play.services.litert)
  implementation(libs.kotlinx.coroutines.play.services)
}
