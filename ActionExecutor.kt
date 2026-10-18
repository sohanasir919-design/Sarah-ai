package com.sarah.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Turns Gemini action JSON into real device actions. Returns an observation string when relevant. */
class ActionExecutor(private val ctx: Context) {

    private val acc get() = SarahAccessibilityService.instance

    suspend fun run(type: String, a: JSONObject): String? {
        when (type.uppercase()) {
            "OPEN_APP" -> openApp(a.optString("app"))
            "READ_SCREEN" -> return acc?.readScreen() ?: "Accessibility service is not enabled."
            "CLICK_TEXT" -> acc?.clickByText(a.optString("text"))
            "TAP" -> acc?.tap(a.optDouble("x").toFloat(), a.optDouble("y").toFloat())
            "SCROLL" -> acc?.scroll(a.optString("direction", "down") == "down")
            "TYPE" -> acc?.typeText(a.optString("text"))
            "BACK" -> acc?.back()
            "HOME" -> acc?.home()
            "WHATSAPP_SEND" -> whatsapp(a.optString("to"), a.optString("message"))
            "YOUTUBE_PLAY" -> youtube(a.optString("query"))
            "DIAL" -> start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.optString("number"))))
            "CALL" -> call(a.optString("number"))
            "ANSWER_CALL" -> telecom { it.acceptRingingCall() }
            "DECLINE_CALL" -> telecom { it.endCall() }
        }
        return null
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }

    private fun launch(pkg: String, webFallback: String? = null) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) start(i) else webFallback?.let { start(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }

    private fun openApp(name: String) {
        when (name.lowercase()) {
            "youtube" -> launch("com.google.android.youtube", "https://www.youtube.com")
            "whatsapp" -> launch("com.whatsapp", "https://web.whatsapp.com")
            "dialer", "phone" -> start(Intent(Intent.ACTION_DIAL))
            "calculator" -> start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            "browser" -> start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
            else -> launch(name)
        }
    }

    // ---- WhatsApp ----------------------------------------------------------------------------
    private suspend fun whatsapp(to: String, message: String) {
        val digits = to.filter { it.isDigit() }
        if (digits.length >= 8) {
            // Direct chat by phone number (with country code) and prefilled message.
            start(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message))).setPackage("com.whatsapp"))
            delay(3000)
        } else {
            // Contact by name: open WhatsApp, search, open chat, type message.
            val s = acc ?: return
            launch("com.whatsapp"); delay(2500)
            if (!s.clickByText("Search")) s.clickFirstMatching { it.contentDescription?.contains("Search", true) == true }
            delay(900); s.typeText(to); delay(1500)
            s.clickFirstMatching { it.text?.contains(to, true) == true }
            delay(1800); s.typeText(message); delay(600)
        }
        val s = acc ?: return
        repeat(4) {
            if (s.clickByText("Send")) return
            delay(500)
        }
    }

    // ---- YouTube -----------------------------------------------------------------------------
    private suspend fun youtube(query: String) {
        start(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query))
        delay(3500)
        val s = acc ?: return
        // Heuristic: result tiles carry a description with duration ("... minutes ... seconds").
        val ok = s.clickFirstMatching { n ->
            val d = n.contentDescription?.toString().orEmpty()
            d.contains("minute", true) || d.contains("second", true) || d.contains("play video", true)
        }
        if (!ok) {
            val dm = ctx.resources.displayMetrics
            s.tap(dm.widthPixels / 2f, dm.heightPixels * 0.30f)
        }
    }

    // ---- Calls -------------------------------------------------------------------------------
    private fun call(number: String) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    @Suppress("MissingPermission")
    private fun telecom(block: (TelecomManager) -> Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try { block(tm) } catch (_: Exception) {
            acc?.clickByText("Answer"); acc?.clickByText("Decline")   // UI fallback
        }
    }
}
