package com.example.sceencap.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * TranslationEngine — xử lý toàn bộ logic dịch thuật.
 *
 * Ưu tiên: Gemini Flash (online, tự nhiên) → ML Kit (offline, cơ bản)
 */
class TranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
    }

    /**
     * Dịch văn bản sang ngôn ngữ đích.
     *
     * @param text          Văn bản cần dịch
     * @param sourceLangCode Mã ngôn ngữ nguồn (ISO 639-1), ví dụ: "en", "ja"
     * @param targetLangCode Mã ngôn ngữ đích, ví dụ: "vi"
     * @param targetLangName Tên hiển thị ngôn ngữ đích, ví dụ: "Tiếng Việt"
     * @param onSuccess     Callback khi dịch thành công, trả về chuỗi dịch + thông tin engine
     * @param onError       Callback khi thất bại
     */
    fun translate(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        targetLangName: String,
        onSuccess: (translatedText: String, usedGemini: Boolean) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        if (isNetworkAvailable()) {
            Log.d(TAG, "Mạng có sẵn → Dùng Gemini Flash")
            translateWithGemini(text, targetLangName, sourceLangCode, targetLangCode, onSuccess, onError)
        } else {
            Log.d(TAG, "Không có mạng → Fallback ML Kit offline")
            translateWithMlKit(text, sourceLangCode, targetLangCode, targetLangName, onSuccess, onError)
        }
    }

    // -------------------------------------------------------------------------
    // Engine 1: Gemini Flash (Online)
    // -------------------------------------------------------------------------

    private fun translateWithGemini(
        text: String,
        targetLangName: String,
        sourceLangCode: String,
        targetLangCode: String,
        onSuccess: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val apiKey = com.example.sceencap.BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API Key trống → Fallback ML Kit")
            translateWithMlKit(text, sourceLangCode, targetLangCode, targetLangName, onSuccess, onError)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Thử lần lượt các model qua REST API trực tiếp (không dùng SDK bị bug)
            val modelsToTry = listOf(
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite"
            )
            var lastError = "Unknown"

            for (modelName in modelsToTry) {
                try {
                    Log.d(TAG, "Gọi REST API model: $modelName")
                    val result = callGeminiRestApi(apiKey, modelName, text, targetLangName)

                    if (result != null && result.isNotEmpty()) {
                        Log.d(TAG, "Gemini ($modelName) thành công")
                        withContext(Dispatchers.Main) {
                            onSuccess(result, true)
                        }
                        return@launch
                    } else {
                        Log.w(TAG, "Gemini ($modelName) trả về rỗng")
                        lastError = "Response rỗng"
                    }
                } catch (e: Exception) {
                    lastError = "[${e.javaClass.simpleName}] ${e.message}"
                    Log.e(TAG, "Gemini ($modelName) lỗi: $lastError", e)
                }
            }

            Log.e(TAG, "Tất cả Gemini model lỗi: $lastError → ML Kit")
            withContext(Dispatchers.Main) {
                translateWithMlKit(text, sourceLangCode, targetLangCode, targetLangName, onSuccess, onError)
            }
        }
    }

    /**
     * Gọi Gemini REST API trực tiếp bằng HttpURLConnection.
     * Tránh hoàn toàn SDK bug MissingFieldException.
     * Trả về chuỗi dịch hoặc null nếu thất bại.
     */
    private fun callGeminiRestApi(
        apiKey: String,
        modelName: String,
        text: String,
        targetLangName: String
    ): String? {
        val endpoint = "https://generativelanguage.googleapis.com/v1/models/$modelName:generateContent?key=$apiKey"

        val prompt = "Translate the following text to $targetLangName. " +
            "Preserve the original tone, emotion, style and context naturally. " +
            "Return ONLY the translated text, nothing else."

        // Build JSON body an toàn bằng org.json
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$prompt\n\n$text")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2048)
            })
        }.toString()

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody)
            writer.flush()
        }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode == 200) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            Log.e(TAG, "Gemini HTTP $responseCode: $errBody")
            throw Exception("HTTP $responseCode: $errBody")
        }
        conn.disconnect()

        // Parse kết quả JSON
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val content = candidates.getJSONObject(0)
            .optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        val resultBuilder = java.lang.StringBuilder()
        for (i in 0 until parts.length()) {
            resultBuilder.append(parts.getJSONObject(i).optString("text", ""))
        }

        return resultBuilder.toString().trim()
    }

    // -------------------------------------------------------------------------
    // Engine 2: ML Kit Translate (Offline Fallback)
    // -------------------------------------------------------------------------

    private fun translateWithMlKit(
        text: String,
        sourceLangCode: String,
        targetLangCode: String,
        targetLangName: String,
        onSuccess: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        // Kiểm tra ML Kit có hỗ trợ cặp ngôn ngữ này không
        val mlKitSourceCode = mapToMlKitCode(sourceLangCode)
        val mlKitTargetCode = mapToMlKitCode(targetLangCode)

        if (mlKitSourceCode == null || mlKitTargetCode == null) {
            onError("Ngôn ngữ \"$targetLangName\" không hỗ trợ offline. Vui lòng kết nối Internet.")
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlKitSourceCode)
            .setTargetLanguage(mlKitTargetCode)
            .build()
        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        onSuccess(translated, false)
                    }
                    .addOnFailureListener { e ->
                        onError("ML Kit dịch thất bại: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onError("Tải model offline thất bại: ${e.message}")
            }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Map mã ISO 639-1 sang hằng số TranslateLanguage của ML Kit.
     * Trả về null nếu ML Kit không hỗ trợ ngôn ngữ đó (offline fallback sẽ báo lỗi thân thiện).
     */
    private fun mapToMlKitCode(isoCode: String): String? {
        return when (isoCode.lowercase()) {
            "af" -> TranslateLanguage.AFRIKAANS
            "sq" -> TranslateLanguage.ALBANIAN
            "ar" -> TranslateLanguage.ARABIC
            "be" -> TranslateLanguage.BELARUSIAN
            "bn" -> TranslateLanguage.BENGALI
            "bg" -> TranslateLanguage.BULGARIAN
            "ca" -> TranslateLanguage.CATALAN
            "zh" -> TranslateLanguage.CHINESE
            "hr" -> TranslateLanguage.CROATIAN
            "cs" -> TranslateLanguage.CZECH
            "da" -> TranslateLanguage.DANISH
            "nl" -> TranslateLanguage.DUTCH
            "en" -> TranslateLanguage.ENGLISH
            "eo" -> TranslateLanguage.ESPERANTO
            "et" -> TranslateLanguage.ESTONIAN
            "fi" -> TranslateLanguage.FINNISH
            "fr" -> TranslateLanguage.FRENCH
            "gl" -> TranslateLanguage.GALICIAN
            "ka" -> TranslateLanguage.GEORGIAN
            "de" -> TranslateLanguage.GERMAN
            "el" -> TranslateLanguage.GREEK
            "gu" -> TranslateLanguage.GUJARATI
            "ht" -> TranslateLanguage.HAITIAN_CREOLE
            "he" -> TranslateLanguage.HEBREW
            "hi" -> TranslateLanguage.HINDI
            "hu" -> TranslateLanguage.HUNGARIAN
            "is" -> TranslateLanguage.ICELANDIC
            "id" -> TranslateLanguage.INDONESIAN
            "ga" -> TranslateLanguage.IRISH
            "it" -> TranslateLanguage.ITALIAN
            "ja" -> TranslateLanguage.JAPANESE
            "kn" -> TranslateLanguage.KANNADA
            "ko" -> TranslateLanguage.KOREAN
            "lv" -> TranslateLanguage.LATVIAN
            "lt" -> TranslateLanguage.LITHUANIAN
            "mk" -> TranslateLanguage.MACEDONIAN
            "ms" -> TranslateLanguage.MALAY
            "mt" -> TranslateLanguage.MALTESE
            "mr" -> TranslateLanguage.MARATHI
            "no" -> TranslateLanguage.NORWEGIAN
            "fa" -> TranslateLanguage.PERSIAN
            "pl" -> TranslateLanguage.POLISH
            "pt" -> TranslateLanguage.PORTUGUESE
            "ro" -> TranslateLanguage.ROMANIAN
            "ru" -> TranslateLanguage.RUSSIAN
            "sk" -> TranslateLanguage.SLOVAK
            "sl" -> TranslateLanguage.SLOVENIAN
            "es" -> TranslateLanguage.SPANISH
            "sw" -> TranslateLanguage.SWAHILI
            "sv" -> TranslateLanguage.SWEDISH
            "tl" -> TranslateLanguage.TAGALOG
            "ta" -> TranslateLanguage.TAMIL
            "te" -> TranslateLanguage.TELUGU
            "th" -> TranslateLanguage.THAI
            "tr" -> TranslateLanguage.TURKISH
            "uk" -> TranslateLanguage.UKRAINIAN
            "ur" -> TranslateLanguage.URDU
            "vi" -> TranslateLanguage.VIETNAMESE
            "cy" -> TranslateLanguage.WELSH
            else -> null // Ngôn ngữ Gemini hỗ trợ nhưng ML Kit không có
        }
    }
}
