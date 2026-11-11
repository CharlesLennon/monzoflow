# MonzoFlow Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn kotlinx.serialization.**
-keepattributes Signature
