# RipeWise ProGuard / R8 release keep rules
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin Serialization & Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Room entities and DAOs
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }

# Firebase AI Logic & App Check
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
