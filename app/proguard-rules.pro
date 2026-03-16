# JSch
-keep class com.jcraft.jsch.** { *; }
-keep class org.ietf.jgss.** { *; }

# EdDSA (ed25519 SSH key support)
-keep class net.i2p.crypto.eddsa.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Timber
-dontwarn org.jetbrains.annotations.**
