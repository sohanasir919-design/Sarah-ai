package com.sarah.assistant

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.sqrt

data class SarahState(
    val status: String = "Tap the mic and talk to Sarah",
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val amplitude: Float = 0f,
    val heard: String = "",
    val said: String = ""
)

/** Pipeline: SpeechRecognizer -> Gemini -> actions -> ElevenLabs -> playback + lip-sync amplitude. */
object SarahBrain {
    private lateinit var app: Context
    private lateinit var gemini: GeminiController
    private lateinit var eleven: ElevenLabsController
    private lateinit var actions: ActionExecutor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var inited = false

    private val _state = MutableStateFlow(SarahState())
    val state: StateFlow<SarahState> = _state

    /** Recognition language: "ur-PK", "hi-IN" or "en-IN" (works well for Roman Urdu). */
    var language = "ur-PK"

    fun init(context: Context) {
        if (inited) return
        app = context.applicationContext
        gemini = GeminiController(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL)
        eleven = ElevenLabsController(BuildConfig.ELEVEN_API_KEY, BuildConfig.ELEVEN_VOICE_ID)
        actions = ActionExecutor(app)
        inited = true
    }

    // ---- Voice input -------------------------------------------------------------------------
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) { set { copy(status = "Speech recognition unavailable") }; return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(app).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) = set { copy(listening = true, status = "Listening...") }
                override fun onResults(r: Bundle?) {
                    set { copy(listening = false) }
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) set { copy(status = "Didn't catch that") } else handle(t)
                }
                override fun onError(e: Int) = set { copy(listening = false, status = "Mic error ($e). Try again") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            })
        }
    }

    // ---- Brain -------------------------------------------------------------------------------
    private fun handle(text: String) = scope.launch {
        set { copy(heard = text, status = "Thinking...") }
        try {
            var reply = gemini.ask(text)
            val obs = actions.run(reply.action, reply.args)
            if (reply.action.equals("READ_SCREEN", true) && obs != null) reply = gemini.ask("[SCREEN]\n$obs")
            if (reply.speech.isNotBlank()) speak(reply.speech) else set { copy(status = "Done") }
        } catch (e: Exception) {
            set { copy(status = "Error: ${e.message?.take(80)}") }
        }
    }

    // ---- Voice output + lip-sync -------------------------------------------------------------
    private suspend fun speak(text: String) {
        set { copy(said = text, status = "Speaking...") }
        val file = File(app.cacheDir, "sarah_tts.mp3")
        try { eleven.synthesize(text, file) }
        catch (e: Exception) { set { copy(status = "TTS error: ${e.message?.take(80)}") }; return }

        val mp = MediaPlayer()
        var viz: Visualizer? = null
        try {
            mp.setDataSource(file.absolutePath); mp.prepare()
            var vizAmp = 0f
            try {
                viz = Visualizer(mp.audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray, r: Int) {
                            var sum = 0.0
                            for (b in w) { val s = (b.toInt() and 0xFF) - 128; sum += s * s }
                            vizAmp = (sqrt(sum / w.size) / 40.0).toFloat().coerceIn(0f, 1f)
                        }
                        override fun onFftDataCapture(v: Visualizer?, f: ByteArray?, r: Int) {}
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                }
            } catch (_: Exception) { /* fall back to synthetic mouth movement */ }

            set { copy(speaking = true) }
            val ticker = scope.launch {
                while (isActive) {
                    val amp = if (vizAmp > 0.03f) vizAmp else (0.25f + Math.random().toFloat() * 0.6f)
                    set { copy(amplitude = amp) }
                    delay(80)
                }
            }
            val done = CompletableDeferred<Unit>()
            mp.setOnCompletionListener { done.complete(Unit) }
            mp.setOnErrorListener { _, _, _ -> done.complete(Unit); true }
            mp.start()
            done.await()
            ticker.cancel()
        } finally {
            try { viz?.enabled = false; viz?.release() } catch (_: Exception) {}
            mp.release()
            set { copy(speaking = false, amplitude = 0f, status = "Tap the mic and talk to Sarah") }
        }
    }

    private inline fun set(f: SarahState.() -> SarahState) { _state.value = _state.value.f() }
}
