import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Instant

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.androidx.baselineprofile)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.aistudio.netgate"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.netgate.xkqmpt"
    minSdk = 24
    targetSdk = 36
    versionCode = 16
    versionName = "3.9.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Build identity surfaced in the About card. CI passes GIT_COMMIT_SHA and
    // BUILD_TIMESTAMP; local builds fall back to git HEAD and the current time.
    val suppliedSha = System.getenv("GIT_COMMIT_SHA")?.trim().orEmpty()
    val gitSha = if (suppliedSha.isNotEmpty()) {
      suppliedSha
    } else if (rootProject.file(".git").exists()) {
      // providers.exec keeps this compatible with the configuration cache; a plain
      // ProcessBuilder is rejected as an unsupported external process.
      val resolved = runCatching {
        providers.exec {
          commandLine("git", "rev-parse", "HEAD")
          isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
      }.getOrDefault("")
      if (resolved.matches(Regex("[0-9a-f]{40}"))) resolved else ""
    } else {
      ""
    }
    val suppliedTimestamp = System.getenv("BUILD_TIMESTAMP")?.trim().orEmpty()
    // Note: the fully qualified `java.time.Instant` does not resolve here, because
    // `java` is the Gradle Java plugin extension inside a build script.
    val buildTimestamp = if (suppliedTimestamp.isNotEmpty()) suppliedTimestamp else Instant.now().toString()
    buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitSha\"")
    buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")
  }

  // WireGuard ships libwg.so/libwg-go.so/libwg-quick.so for every ABI (~11.5 MB in
  // total), so a single universal APK pays for all four architectures. Emit one APK
  // per ABI and keep a universal APK in CI/Release builds, while keeping standard
  // single app-debug.apk output for local/Streaming Emulator debug previews.
  val isCiOrRelease = providers.environmentVariable("GITHUB_ACTIONS").orNull == "true" ||
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
  splits {
    abi {
      isEnable = isCiOrRelease
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Shrink and obfuscate release builds with R8. Resource shrinking needs
      // minification enabled; the resulting mapping.txt is uploaded by the
      // release workflow so crash traces can be retraced.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    // CI runs `:app:lintDebug`, so error-severity findings must fail the build
    // instead of just being printed. Warnings stay non-blocking.
    abortOnError = true
    warningsAsErrors = false
    // These only report newer upstream versions, which Dependabot covers.
    disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // Installs the committed Baseline Profile on first launch (and on every
  // profile change) for devices without Cloud profiles, API 24 through 30.
  implementation(libs.androidx.profileinstaller)
  implementation(libs.wireguard.tunnel)
  implementation(libs.vpnprotocols.openvpn)
  implementation(libs.fletchmckee.liquid)
  // Renders the hotspot setup QR card locally (no network calls).
  implementation(libs.zxing.core)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Merges the Baseline Profile generated by :baselineprofile into release builds.
  "baselineProfile"(project(":baselineprofile"))
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
