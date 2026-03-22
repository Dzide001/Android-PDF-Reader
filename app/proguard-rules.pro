-keep class com.pdfreader.** { *; }
-keep interface com.pdfreader.** { *; }

# Preserve MuPDF JNI native methods
-keepclasseswithmembernames class ** {
    native <methods>;
}

# Jetpack Compose
-keep class androidx.compose.** { *; }

# Room Database
-keep class ** extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# WorkManager
-keep class androidx.work.** { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }

# Keep generic signature for use by reflection
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
