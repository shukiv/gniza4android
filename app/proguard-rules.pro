# JSch
-keep class com.jcraft.jsch.** { *; }
-keep class org.ietf.jgss.** { *; }

# EdDSA (ed25519 SSH key support)
-keep class net.i2p.crypto.eddsa.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Timber
-dontwarn org.jetbrains.annotations.**
