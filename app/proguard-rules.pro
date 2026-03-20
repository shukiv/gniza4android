# JSch
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.newsclub.net.unix.**

# EdDSA (ed25519 SSH key support)
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn sun.security.x509.**

# zxing-cpp
-keep class zxingcpp.** { *; }

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
