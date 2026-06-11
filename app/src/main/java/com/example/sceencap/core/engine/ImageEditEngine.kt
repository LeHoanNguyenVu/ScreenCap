package com.example.sceencap.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * ImageEditEngine — gọi Gemini REST API để chỉnh sửa ảnh bằng AI.
 *
 * Gửi ảnh (base64) + prompt → nhận ảnh kết quả (base64) từ Gemini.
 * Fallback qua nhiều model nếu model chính lỗi.
 */
class ImageEditEngine {

    companion object {
        private const val TAG = "ImageEditEngine"
        private const val MAX_IMAGE_SIZE = 1024 // px — resize trước khi gửi
    }

    /**
     * Chỉnh sửa ảnh bằng AI.
     *
     * @param bitmap   Ảnh gốc cần sửa
     * @param prompt   Mô tả chỉnh sửa (VD: "Xóa nền", "Thêm kính mắt")
     * @param apiKey   Gemini API Key
     * @param onSuccess Callback trả Bitmap kết quả
     * @param onError   Callback trả message lỗi
     */
    fun editImage(
        bitmap: Bitmap,
        prompt: String,
        apiKey: String,
        onSuccess: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onError("API Key trống. Không thể sử dụng AI edit.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Dùng 1 model duy nhất để tiết kiệm quota
            val modelName = "gemini-3.1-flash-image"

            val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
            val base64Image = bitmapToBase64(resizedBitmap)

            try {
                Log.d(TAG, "Gọi Gemini ($modelName) để edit ảnh...")
                val resultBitmap = callGeminiImageEdit(apiKey, modelName, base64Image, prompt)

                if (resultBitmap != null) {
                    Log.d(TAG, "Gemini ($modelName) thành công — ảnh ${resultBitmap.width}x${resultBitmap.height}")
                    withContext(Dispatchers.Main) {
                        onSuccess(resultBitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Gemini không trả về ảnh kết quả. Thử mô tả rõ hơn nhé!")
                    }
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Lỗi không xác định"
                Log.e(TAG, "$modelName lỗi: $msg", e)
                withContext(Dispatchers.Main) {
                    // Thông báo thân thiện cho từng loại lỗi
                    val userMsg = when {
                        msg.contains("quota", ignoreCase = true) ->
                            "⏳ Hết lượt dùng AI hôm nay. Quota sẽ reset lúc 0h (UTC). Thử lại ngày mai nhé!"
                        msg.contains("429") ->
                            "⏳ Gửi quá nhanh, đợi 1 phút rồi thử lại!"
                        msg.contains("403") ->
                            "🔑 API Key không có quyền dùng image generation."
                        else -> "❌ $msg"
                    }
                    onError(userMsg)
                }
            }
        }
    }

    /**
     * Gọi Gemini REST API với ảnh + prompt, nhận ảnh kết quả.
     * Dùng endpoint v1beta (bắt buộc cho image output).
     */
    private fun callGeminiImageEdit(
        apiKey: String,
        modelName: String,
        base64Image: String,
        prompt: String
    ): Bitmap? {
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        // Ảnh đầu vào
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        // Prompt chỉnh sửa
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
            })
        }.toString()

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000  // Ảnh xử lý lâu hơn text

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody)
            writer.flush()
        }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode == 200) {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } else {
            val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            Log.e(TAG, "HTTP $responseCode: $errBody")
            conn.disconnect()
            // Parse error message nếu có
            val errMsg = try {
                val errJson = JSONObject(errBody)
                errJson.optJSONObject("error")?.optString("message", "HTTP $responseCode") ?: "HTTP $responseCode"
            } catch (_: Exception) { "HTTP $responseCode" }
            throw Exception(errMsg)
        }
        conn.disconnect()

        // Parse response — tìm part có inlineData (ảnh kết quả)
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inlineData = part.optJSONObject("inlineData")
            if (inlineData != null) {
                val imageBase64 = inlineData.optString("data", "")
                if (imageBase64.isNotEmpty()) {
                    return base64ToBitmap(imageBase64)
                }
            }
        }

        return null
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Decode base64 → Bitmap thất bại", e)
            null
        }
    }
}
