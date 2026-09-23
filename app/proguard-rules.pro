# ---- NAVER Shopping Connect AI Studio — R8 rules ----
# kotlinx.serialization: keep generated serializers of our @Serializable models (JSON exchanged with Claude, Room blobs, backups).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.shoppingconnect.aistudio.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.shoppingconnect.aistudio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shoppingconnect.aistudio.**$$serializer { *; }
# Enum names are persisted in Room/DataStore/JSON — keep them stable.
-keepclassmembers enum com.shoppingconnect.aistudio.** { *; }

# jsoup optional dependency
-dontwarn com.google.re2j.**
# OkHttp platform adapters
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Never keep logging calls with arguments in release (AppLog already redacts; strip Log.d/v entirely).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
