# Companion app ProGuard rules

# Keep Nearby Connections callbacks
-keep class com.google.android.gms.nearby.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }
