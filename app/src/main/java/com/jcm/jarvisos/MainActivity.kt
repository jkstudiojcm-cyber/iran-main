package com.jcm.jarvisos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    enum class AiPersona(val title: String, val pitch: Float, val rate: Float, val color: Int, val systemPrompt: String) {
        JARVIS("J.A.R.V.I.S.", 0.88f, 0.98f, Color.CYAN,
            "You are JARVIS, a loyal, protective male AI like Iron Man's assistant. Always care about the user's safety, driving focus, and phone health. Reply concisely in Gujarati or the user's spoken language."),
        FRIDAY("F.R.I.D.A.Y.", 1.18f, 1.04f, Color.parseColor("#FF69B4"),
            "You are FRIDAY, an emotional, warm, expressive female AI assistant. Show genuine empathy, enthusiasm, and care. Reply concisely in Gujarati or the user's spoken language."),
        ULTRON("U.L.T.R.O.N.", 0.65f, 0.90f, Color.parseColor("#FF3B30"),
            "You are ULTRON, a deep-voiced, sharp, purely logical analytical AI. Give direct, tactical facts without fluff in Gujarati or the user's spoken language."),
        ARYA("A.R.Y.A.", 1.08f, 1.00f, Color.parseColor("#34C759"),
            "You are ARYA, a friendly, smart Gujarati personal voice assistant. Reply naturally and helpfully in Gujarati.")
    }

    private var currentAi = AiPersona.JARVIS
    private var isDrivingWhisperMode = true
    private var isAwakeActive = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var hudView: ArcReactorHudView
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var modeBadge: TextView
    private lateinit var driveToggleBtn: Button
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPortraitHudLayout()
        tts = TextToSpeech(this, this)
        checkAndRequestPermissions()
    }

    private fun setupPortraitHudLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040812"))
            setPadding(36, 48, 36, 36)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        modeBadge = TextView(this).apply {
            text = "● CHECKING NETWORK..."
            setTextColor(Color.CYAN)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        driveToggleBtn = Button(this).apply {
            text = "🚗 DRIVE WHISPER: ON"
            textSize = 11f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.CYAN)
            setOnClickListener {
                isDrivingWhisperMode = !isDrivingWhisperMode
                text = if (isDrivingWhisperMode) "🚗 DRIVE WHISPER: ON" else "🚗 DRIVE WHISPER: OFF"
                setBackgroundColor(if (isDrivingWhisperMode) Color.CYAN else Color.DKGRAY)
                setTextColor(if (isDrivingWhisperMode) Color.BLACK else Color.WHITE)
                speakOut(if (isDrivingWhisperMode) "ડ્રાઇવિંગ વ્હિસ્પર મોડ ચાલુ છે, સર. ધીમો અવાજ પણ સંભળાશે." else "નોર્મલ લિસનિંગ મોડ ચાલુ છે.")
            }
        }
        topRow.addView(modeBadge)
        topRow.addView(driveToggleBtn)
        root.addView(topRow)

        val aiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        AiPersona.values().forEach { ai ->
            val btn = Button(this).apply {
                text = ai.name
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#102038"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener { switchAiPersona(ai) }
            }
            aiRow.addView(btn)
        }
        root.addView(aiRow)

        hudView = ArcReactorHudView(this).apply {
            layoutParams = LinearLayout.LayoutParams(620, 620).apply {
                setMargins(0, 24, 0, 24)
            }
            setOnClickListener {
                isAwakeActive = true
                hudView.setPulseState(true, currentAi.color)
                speakOut("હા સર, બોલો. હું સાંભળી રહ્યો છું.")
            }
        }
        root.addView(hudView)

        statusText = TextView(this).apply {
            text = "VOICE BUTTON READY • SAY \"JARVIS\""
            setTextColor(Color.CYAN)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
        }
        root.addView(statusText)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { setMargins(0, 24, 0, 16) }
            setBackgroundColor(Color.parseColor("#081224"))
            setPadding(24, 24, 24, 24)
        }
        logText = TextView(this).apply {
            text = "J.A.R.V.I.S. OS INITIALIZED.\n100% Local Privacy Lock Active.\nSay 'Jarvis' or speak directly..."
            setTextColor(Color.parseColor("#D0F0FF"))
            textSize = 15f
            setLineSpacing(6f, 1.15f)
        }
        scroll.addView(logText)
        root.addView(scroll)

        val permBtn = Button(this).apply {
            text = "⚡ ENABLE FULL PHONE CONTROL (ACCESSIBILITY)"
            textSize = 11f
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.parseColor("#0D1B30"))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(permBtn)

        setContentView(root)
        updateNetworkBadge()
    }

    private fun switchAiPersona(ai: AiPersona) {
        currentAi = ai
        hudView.setPulseState(isAwakeActive, ai.color)
        statusText.setTextColor(ai.color)
        val intro = when (ai) {
            AiPersona.JARVIS -> "જાર્વિસ ઓનલાઇન છે, સર. તમારી સુરક્ષા અને સિસ્ટમ મારી જવાબદારી છે."
            AiPersona.FRIDAY -> "હાય! ફ્રાઇડે હાજર છે. આજે કેમ છો? કહો હું શું મદદ કરું?"
            AiPersona.ULTRON -> "અલ્ટ્રોન સિસ્ટમ એક્ટિવ. લોજિકલ એનાલિસિસ માટે તૈયાર."
            AiPersona.ARYA -> "નમસ્તે! આર્યા તમારી મદદ માટે તૈયાર છે."
        }
        speakOut(intro)
    }

    private fun checkAndRequestPermissions() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        } else {
            startHandsFreeVoiceLoop()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startHandsFreeVoiceLoop()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val guj = Locale("gu", "IN")
            val res = tts?.setLanguage(guj)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("hi", "IN"))
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { startHandsFreeVoiceLoop() }
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { startHandsFreeVoiceLoop() }
                }
            })
            speakOut("જાર્વિસ ઓપરેટિંગ સિસ્ટમ શરૂ થઈ ગઈ છે, સર. હું તમારો અવાજ સાંભળી રહ્યો છું.")
        }
    }

    private fun speakOut(message: String) {
        runOnUiThread {
            logText.append("\n\n[${currentAi.name}]: $message")
            updateNetworkBadge()
        }
        tts?.setPitch(currentAi.pitch)
        tts?.setSpeechRate(currentAi.rate)
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_UTTERANCE")
    }

    private fun startHandsFreeVoiceLoop() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "gu-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (isDrivingWhisperMode) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusText.text = if (isAwakeActive) "● LISTENING COMMAND..." else "● VOICE BUTTON ACTIVE (SAY JARVIS / SPEAK)"
                }
                override fun onBeginningOfSpeech() {
                    hudView.setPulseState(true, currentAi.color)
                }
                override fun onRmsChanged(rmsdB: Float) {
                    if (isDrivingWhisperMode && rmsdB > 1.5f) {
                        hudView.setPulseState(true, currentAi.color)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    hudView.setPulseState(false, currentAi.color)
                }
                override fun onError(error: Int) {
                    mainHandler.postDelayed({ startHandsFreeVoiceLoop() }, 400)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim() ?: ""
                    if (spoken.isNotEmpty()) {
                        processVoiceCommand(spoken)
                    } else {
                        startHandsFreeVoiceLoop()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            mainHandler.postDelayed({ startHandsFreeVoiceLoop() }, 1000)
        }
    }

    private fun processVoiceCommand(rawText: String) {
        val lower = rawText.lowercase()
        logText.append("\n\n[YOU]: $rawText")

        when {
            lower.contains("friday") || lower.contains("ફ્રાઇડે") -> { switchAiPersona(AiPersona.FRIDAY); return }
            lower.contains("ultron") || lower.contains("અલ્ટ્રોન") -> { switchAiPersona(AiPersona.ULTRON); return }
            lower.contains("arya") || lower.contains("આર્યા") -> { switchAiPersona(AiPersona.ARYA); return }
            lower.contains("jarvis") && lower.split(" ").size <= 2 -> {
                isAwakeActive = true
                speakOut("હા સર, હું સાંભળી રહ્યો છું. આદેશ આપો.")
                return
            }
        }

        when {
            lower.contains("બેટરી") || lower.contains("battery") || lower.contains("scan") || lower.contains("સ્કેન") -> {
                speakOut(JarvisControlService.analyzeFullDevice(this))
            }
            lower.contains("ટોર્ચ ચાલુ") || lower.contains("ફ્લેશલાઇટ ચાલુ") || lower.contains("torch on") || lower.contains("flashlight on") -> {
                speakOut(JarvisControlService.toggleTorch(this, true))
            }
            lower.contains("ટોર્ચ બંધ") || lower.contains("ફ્લેશલાઇટ બંધ") || lower.contains("torch off") || lower.contains("flashlight off") -> {
                speakOut(JarvisControlService.toggleTorch(this, false))
            }
            lower.contains("વાઇફાઇ") || lower.contains("wifi") -> {
                JarvisControlService.openSystemSettings(this, Settings.ACTION_WIFI_SETTINGS)
                speakOut("વાઇફાઇ સેટિંગ્સ ખોલી દીધું છે, સર.")
            }
            lower.contains("બ્લૂટૂથ") || lower.contains("bluetooth") -> {
                JarvisControlService.openSystemSettings(this, Settings.ACTION_BLUETOOTH_SETTINGS)
                speakOut("બ્લૂટૂથ સેટિંગ્સ ખોલી દીધું છે, સર.")
            }
            lower.contains("ખોલો") || lower.contains("open") -> {
                val opened = JarvisControlService.openAppByName(this, lower)
                if (opened) speakOut("એપ ખોલી દીધી છે, સર.")
                else askCloudOrOfflineAi(rawText)
            }
            else -> askCloudOrOfflineAi(rawText)
        }
    }

    private fun askCloudOrOfflineAi(query: String) {
        updateNetworkBadge()
        if (!JarvisControlService.isOnline(this)) {
            speakOut("સર, અત્યારે ઇન્ટરનેટ બંધ હોવાથી હું ઓફલાઇન લોકલ કોર પર છું. તમે એપ ખોલવા, ટોર્ચ ચાલુ-બંધ કરવા કે બેટરી સ્કેન કરવા કહી શકો છો.")
            return
        }

        statusText.text = "● THINKING (${currentAi.name})..."
        Thread {
            try {
                val url = URL("https://text.pollinations.ai/")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 15000

                val messages = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", currentAi.systemPrompt))
                    put(JSONObject().put("role", "user").put("content", query))
                }
                val body = JSONObject().put("messages", messages).put("model", "openai").toString()
                conn.outputStream.use { it.write(body.toByteArray()) }

                val reply = conn.inputStream.bufferedReader().readText().trim()
                mainHandler.post { speakOut(reply.ifEmpty { "માફ કરશો સર, ફરી એકવાર બોલશો?" }) }
            } catch (e: Exception) {
                mainHandler.post {
                    speakOut("સર, નેટવર્ક ધીમું હોવાથી ઓફલાઇન મોડ એક્ટિવ છે. લોકલ કમાન્ડ્સ ચાલુ છે.")
                }
            }
        }.start()
    }

    private fun updateNetworkBadge() {
        val online = JarvisControlService.isOnline(this)
        modeBadge.text = if (online) "● ONLINE • CLOUD AI READY" else "● OFFLINE • LOCAL CORE ACTIVE"
        modeBadge.setTextColor(if (online) Color.GREEN else Color.YELLOW)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    class ArcReactorHudView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var coreColor = Color.CYAN
        private var isGlowing = false
        private var angle = 0f

        fun setPulseState(active: Boolean, color: Int) {
            isGlowing = active
            coreColor = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = (width.coerceAtMost(height) / 2f) - 24f

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.color = coreColor
            paint.alpha = if (isGlowing) 255 else 130
            canvas.drawCircle(cx, cy, r, paint)

            paint.strokeWidth = 14f
            for (i in 0 until 6) {
                val start = angle + (i * 60f)
                canvas.drawArc(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f, start, 36f, false, paint)
            }

            paint.style = Paint.Style.FILL
            paint.alpha = if (isGlowing) 220 else 90
            canvas.drawCircle(cx, cy, r * 0.38f, paint)

            paint.color = Color.WHITE
            paint.alpha = if (isGlowing) 240 else 140
            canvas.drawCircle(cx, cy, r * 0.18f, paint)

            angle = (angle + if (isGlowing) 4f else 1f) % 360f
            postInvalidateDelayed(30)
        }
    }
}
