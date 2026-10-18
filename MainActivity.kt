package com.sarah.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SarahBrain.init(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { SarahScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SarahScreen() {
        val s by SarahBrain.state.collectAsState()
        var lang by remember { mutableStateOf(SarahBrain.language) }
        val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
        LaunchedEffect(Unit) {
            perms.launch(arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS
            ))
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B14))) {
            Avatar3DView(speaking = s.speaking, amplitude = s.amplitude, modifier = Modifier.fillMaxSize())

            Column(
                Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (s.heard.isNotBlank()) Bubble("You: ${s.heard}")
                if (s.said.isNotBlank()) Bubble("Sarah: ${s.said}")
                Text(s.status, color = Color.LightGray, textAlign = TextAlign.Center)

                FloatingActionButton(onClick = { if (!s.listening) SarahBrain.startListening() }) {
                    Icon(Icons.Default.Mic, "Talk to Sarah")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text(if (SarahAccessibilityService.isRunning) "Automation ON" else "Enable automation") }
                    OutlinedButton(onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")))
                        } else startService(Intent(this@MainActivity, SarahOverlayService::class.java))
                    }) { Text("Floating Sarah") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, code) in listOf("اردو" to "ur-PK", "हिन्दी" to "hi-IN", "Roman" to "en-IN"))
                        FilterChip(selected = lang == code,
                            onClick = { lang = code; SarahBrain.language = code }, label = { Text(label) })
                }
            }
        }
    }

    @Composable
    private fun Bubble(t: String) = Surface(color = Color(0xAA222233), shape = RoundedCornerShape(14.dp)) {
        Text(t, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White)
    }
}
