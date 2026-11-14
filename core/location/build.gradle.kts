plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.pratclot.cafes.core.location"
  compileSdk = 37

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  api(project(":core:model"))
  implementation(project(":core:passthrough-android"))
  implementation(libs.play.services.location)
}
