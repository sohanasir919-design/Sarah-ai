package com.sarah.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ElevenLabsController(private val apiKey: String, private val voiceId: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** Synthesizes [text] (Urdu / Hindi / Roman Urdu) to an MP3 file and returns it. */
    suspend fun synthesize(text: String, out: File): File = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", 0.45); put("similarity_boost", 0.8); put("style", 0.3)
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(body).build()

        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("ElevenLabs ${r.code}: ${r.body?.string()?.take(200)}")
            out.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
        out
    }
}
