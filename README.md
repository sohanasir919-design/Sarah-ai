# Sarah 3D AI Assistant (Kotlin + Jetpack Compose + SceneView)

## Setup
1. Copy `local.properties.example` -> `local.properties`; set `sdk.dir` and the Gemini / ElevenLabs keys + voice id.
2. Put a rigged GLB avatar at `app/src/main/assets/models/sarah.glb` (see the README.txt in that folder).
3. Android Studio: open the folder and Run. Termux/CLI: `gradle wrapper --gradle-version 8.7 && ./gradlew assembleDebug`.
4. On device: grant mic/phone permissions; "Enable automation" -> turn on *Sarah Automation*;
   "Floating Sarah" -> allow "Display over other apps".

## Pipeline
Mic -> SpeechRecognizer -> Gemini (JSON: speech + action) -> ActionExecutor (Accessibility / Intents / Telecom)
-> ElevenLabs MP3 -> MediaPlayer + Visualizer amplitude -> avatar morph-target lip-sync.

## Notes
- `GEMINI_MODEL` lives in local.properties because model names get retired; update when needed.
- Android 13+: for sideloaded builds enable Settings > Apps > Sarah > menu > "Allow restricted settings" before turning on Accessibility.
- WhatsApp/YouTube UI automation is heuristic and can break when those apps change their UI.
- Don't ship API keys inside a public APK; proxy via your own backend for production.
