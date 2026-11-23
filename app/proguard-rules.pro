# R8 / ProGuard rules for NetGate release builds (isMinifyEnabled + isShrinkResources).
# AndroidX, Compose, Room, OkHttp and coroutines ship their own consumer rules, so
# only project-specific keep rules and trace metadata belong here.
#
# Reference: https://developer.android.com/build/shrink-code

# Keep the file/line metadata so crashes from release builds can be retraced
# against the mapping.txt uploaded by the release workflow. Source file names
# are replaced with the literal "SourceFile" to avoid leaking paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep JNI methods; native code resolves them by name at runtime.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ViewModels are instantiated reflectively by ViewModelProvider's default factory.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# WireGuard's tunnel library is loaded through JNI and parses its config classes
# by name, so it has to survive shrinking and obfuscation untouched.
-keep class com.wireguard.android.** { *; }
-keep class com.wireguard.config.** { *; }
-keep class com.wireguard.crypto.** { *; }
-dontwarn com.wireguard.android.**
-dontwarn com.wireguard.config.**
-dontwarn com.wireguard.crypto.**
