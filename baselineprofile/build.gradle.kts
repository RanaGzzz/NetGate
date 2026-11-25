plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.androidx.baselineprofile)
}

// Producer module for the ART Baseline Profile that ships in release builds.
// It contributes no code to the app: it only drives :app on a device and records
// the classes and methods reached during a cold start.
android {
  namespace = "com.aistudio.netgate.baselineprofile"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // Profile generation needs API 33+ (or a rooted device). This module is only
    // ever installed on a test device, never shipped to users.
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // The app module this profile is generated for.
  targetProjectPath = ":app"

  testOptions {
    // Keeps the captured journey free of system animation frames.
    animationsDisabled = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

baselineProfile {
  // Generate on the attached emulator or device (CI boots an emulator, see
  // .github/workflows/baseline-profile.yml). The output is committed to
  // app/src/release/generated/baselineProfiles/baseline-prof.txt.
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
