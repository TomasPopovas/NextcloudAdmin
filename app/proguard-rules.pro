# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink (EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.crypto.tink.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ru.tomas.ncadmin.**$$serializer { *; }
-keepclassmembers class ru.tomas.ncadmin.** { *** Companion; }
-keepclasseswithmembers class ru.tomas.ncadmin.** { kotlinx.serialization.KSerializer serializer(...); }
