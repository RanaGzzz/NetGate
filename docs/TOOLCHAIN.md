# NetGate — Build Toolchain

The repository previously shipped **no Gradle wrapper** and required a locally installed Gradle, JDK and Android SDK. This document records the exact toolchain and every local prerequisite so any machine (or CI) can reproduce a build and a test run.

## Versions (from the project, do not change casually)

| Component | Version | Source |
| :--- | :--- | :--- |
| Gradle | **9.3.1** | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 9.1.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.2.10 (Compose plugin same version) | `gradle/libs.versions.toml` |
| KSP | 2.3.5 | `gradle/libs.versions.toml` |
| JDK | **21** | `README.md`, CI workflow |
| compileSdk | 36 (`minorApiLevel = 1`) → SDK platform **android-36.1** | `app/build.gradle.kts` |
| targetSdk / minSdk | 36 / 24 | `app/build.gradle.kts` |
| Build-tools | 36.0.0 (AGP default) or 36.1.0 | installed by AGP |
| Compose BOM | 2025.12.00 | `gradle/libs.versions.toml` |

## One-time local prerequisites

These are **not** committed (they are in `.gitignore`) and must exist on each machine:

```shell
# 1. Local env file for the Secrets Gradle Plugin
test -f .env || cp .env.example .env

# 2. Debug signing key (keep it, so you can update-install over previous builds)
keytool -genkeypair -v -keystore debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

# 3. SDK location (either local.properties or ANDROID_HOME)
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
```

Android SDK packages required:

```shell
sdkmanager "platform-tools" \
           "platforms;android-36.1" \
           "platforms;android-36" \
           "build-tools;36.1.0"
yes | sdkmanager --licenses
```

## Build and test

```shell
# unit + Robolectric suite (101 tests)
./gradlew :app:testDebugUnitTest

# debug APKs (ABI splits are on: one per architecture plus a universal APK)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-universal-debug.apk
# → app/build/outputs/apk/debug/app-<abi>-debug.apk
```

## Device verification

Two checks need a running emulator or device and therefore live in their own workflows:

```shell
# Launch, VPN-consent and Wi-Fi permission smoke tests (workflow: Device smoke tests)
./gradlew :app:connectedDebugAndroidTest

# Cold-start macrobenchmark; compare the metrics table between runs
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

## Baseline profile

Release builds ship an ART **Baseline Profile** (and Startup Profile) so the first frames after launch do not wait for JIT warm-up. The setup has three parts:

- `:app` applies the `androidx.baselineprofile` plugin and depends on the producer module via `"baselineProfile"(project(":baselineprofile"))`.
- `:baselineprofile` is a `com.android.test` module that only drives `:app`; it contains no app code.
- `androidx.profileinstaller` is a runtime dependency of `:app`, so the profile is installed on first launch, including API 24–30 and devices without Cloud profiles.

Generate the profile on a connected emulator or device running **API 33+** (or a rooted device):

```shell
# Requires an attached device/emulator; boots the app and records a cold start
./gradlew :app:generateBaselineProfile
```

The task writes the result to
`app/src/release/generated/baselineProfiles/baseline-prof.txt` and
`app/src/release/generated/baselineProfiles/startup-prof.txt`. **Commit those files** — they are
source, not build output. Regenerate them when the startup journey changes materially (new
screens on the launch path, a new startup service, a Compose upgrade).

When you change the generator, edit
`baselineprofile/src/main/java/com/aistudio/netgate/baselineprofile/NetGateBaselineProfileGenerator.kt`.
Only paths that actually run during startup belong in the block marked with
`includeInStartupProfile = true`; anything else makes the startup profile larger without helping launch.

CI can regenerate the profile on demand: run the **Generate Baseline Profile** workflow
(`.github/workflows/baseline-profile.yml`) from the Actions tab, download the `baseline-profile`
artifact and commit the files it contains. The workflow boots an API 34 AOSP emulator and signs the
non-minified release variant with the throwaway debug keystore, so it needs no release secrets.

Because this is a performance change, measure before/after on a physical device (Android Studio's
Startup Profiler, or macrobenchmark with `StartupTimingMetric`) rather than assuming the profile
helped.

## Notes and known environment issues

- **`google-services.json` is absent by design.** The Google Services plugin is configured with `MissingGoogleServicesStrategy.WARN`, so the build prints a warning and continues. Do not add Firebase config unless a feature needs it.
- **Maven Central rate limiting (HTTP 429).** On fresh machines `org.robolectric:nativeruntime-dist-compat:1.0.18` (a ~150 MB jar) can fail with `429 Too Many Requests`. Workaround without touching project files: mirror Maven Central first via an init script.

  ```groovy
  // /tmp/central-mirror.init.gradle
  settingsEvaluated { settings ->
      def repos = settings.dependencyResolutionManagement.repositories
      repos.clear()
      repos.maven { url = uri('https://maven-central.storage-download.googleapis.com/maven2'); name = 'centralMirror' }
      repos.google()
      repos.mavenCentral()
  }
  ```

  ```shell
  ./gradlew :app:testDebugUnitTest -I /tmp/central-mirror.init.gradle
  ```

  To make plain `./gradlew` work everywhere on that machine, install the same script as a user-level init script instead (`~/.gradle/init.d/central-mirror.gradle`). This is machine configuration, not project configuration — nothing is committed and the repository's own repositories stay untouched.

- **Memory.** `gradle.properties` sets `org.gradle.jvmargs=-Xmx4g`. On a small VM (≤3 GB RAM) override per run: `-Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`, optionally with `--no-parallel --no-configuration-cache`.
- **CI gap.** `.github/workflows/build-apk.yml` only runs `gradle assembleDebug` against a system Gradle. It does not run tests. Consider switching it to `./gradlew` now that the wrapper is committed, and adding `./gradlew :app:testDebugUnitTest`.
