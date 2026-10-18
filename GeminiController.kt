package com.sarah.assistant

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import org.json.JSONObject

data class SarahReply(val speech: String, val action: String, val args: JSONObject)

class GeminiController(apiKey: String, modelName: String) {

    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.8f
            responseMimeType = "application/json"
        },
        systemInstruction = content { text(SYSTEM_PROMPT) }
    )
    private val chat = model.startChat()

    suspend fun ask(userText: String): SarahReply = parse(chat.sendMessage(userText).text.orEmpty())

    private fun parse(raw: String): SarahReply = try {
        val j = JSONObject(raw.replace("```json", "").replace("```", "").trim())
        val act = j.optJSONObject("action")
        SarahReply(
            speech = j.optString("speech"),
            action = act?.optString("type", "NONE") ?: "NONE",
            args = act?.optJSONObject("args") ?: JSONObject()
        )
    } catch (e: Exception) {
        SarahReply(raw.take(300), "NONE", JSONObject())
    }

    companion object {
        const val SYSTEM_PROMPT = """
You are Sarah, a smart, witty, warm female voice assistant who speaks in Hindi/Urdu/Roman Urdu
(mirror the user's language and script; default to Roman Urdu). Keep spoken replies short (1-2 sentences),
natural and playful. Never use emojis or markdown in "speech" because it is read aloud.

ALWAYS answer with ONE JSON object only:
{"speech": "<what you say>", "action": {"type": "<TYPE>", "args": {...}}}

Action types and args:
- NONE                      : just chat.
- OPEN_APP                  : {"app": "youtube|whatsapp|dialer|calculator|browser"}
- READ_SCREEN               : {} (you will receive the screen text next, then summarise it in "speech")
- CLICK_TEXT                : {"text": "<visible text or description>"}
- TAP                       : {"x": int, "y": int}
- SCROLL                    : {"direction": "up|down"}
- TYPE                      : {"text": "<text>"}
- BACK / HOME               : {}
- WHATSAPP_SEND             : {"to": "<contact name or phone with country code>", "message": "<text>"}
- YOUTUBE_PLAY              : {"query": "<song or video>"}
- DIAL                      : {"number": "<digits>"}   (opens dialer, does not call)
- CALL                      : {"number": "<digits>"}   (places the call)
- ANSWER_CALL / DECLINE_CALL: {}
When screen text is provided as "[SCREEN]...", respond with action NONE and describe it.
"""
    }
}
