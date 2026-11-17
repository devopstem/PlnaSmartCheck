# PLNA Smart Check — правила R8/ProGuard для release.
# Цель: обфускация и удаление мёртвого кода (полная защита от декомпиляции невозможна).

# --- Kotlin / Coroutines ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }

# --- Keep line numbers for crash reports (без исходных имён) ---
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Tesseract / JNI ---
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- Data models written to Firestore / JSON ---
-keepclassmembers class com.plna.smartcheck.data.model.** { *; }

# --- Enums used in UI / storage ---
-keepclassmembers enum com.plna.smartcheck.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
