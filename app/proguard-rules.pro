# ============================================================
# ProGuard Rules — SceenCap
# ============================================================

# --- Giữ thông tin debug stack trace ---
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# --- ML Kit: Text Recognition ---
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }

# --- ML Kit: Translation + Language ID ---
-keep class com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.nl.languageid.** { *; }
-keep class com.google.android.gms.internal.mlkit_nl_translate.** { *; }

# --- ML Kit: Barcode ---
-keep class com.google.mlkit.vision.barcode.** { *; }

# --- ML Kit common ---
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# --- Gemini AI SDK ---
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.ai.client.generativeai.type.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# --- Kotlin Coroutines ---
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- OkHttp (Gemini SDK dùng ngầm) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Retrofit (Gemini SDK dùng ngầm) ---
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# --- Giữ Enum ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}