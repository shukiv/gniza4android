# JSch
-keep class com.jcraft.jsch.** { *; }
-keep class org.ietf.jgss.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Timber
-dontwarn org.jetbrains.annotations.**
