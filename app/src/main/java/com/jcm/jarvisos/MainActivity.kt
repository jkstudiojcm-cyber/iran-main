package com.jcm.jarvisos

import android.Manifest
import android.content.Context
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

    enum class AiPersona(
        val title: String,
        val pitch: Float,
        val rate: Float,
        val color: Int,
        val systemPrompt: String
    ) {
        JARVIS(
            "J.A.R.V.I.S.",
            0.88f,
            0.98f,
            Color.CYAN,
            "You are JARVIS, a loyal, protective male AI like Iron Man's assistant. Reply concisely in Gujarati or the user's spoken language."
        ),
        FRIDAY(
            "F.R.I.D.A.Y.",
            1.18f,
            1.04f,
            Color.parseColor("#FF69B4"),
            "You are FRIDAY, an emotional, warm, expressive female AI assistant. Reply concisely in Gujarati or the user's spoken language."
        ),
        ULTRON(
            "U.L.T.R.O.N.",
            0.65f,
            0.90f,
            Color.parseColor("#FF3B30"),
            "You are ULTRON, a deep-voiced, sharp, purely logical analytical AI. Reply concisely in Gujarati or the user's spoken language."
        ),
        ARYA(
            "A.R.Y.A.",
            1.08f,
            1.00f,
            Color.parseColor("#34C759"),
            "You are ARYA, a friendly, smart Gujarati personal voice assistant. Reply naturally and helpfully in Gujarati."
        )
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var arcReactorView: ArcReactorView
    private lateinit var tvStatus: TextView
    private lateinit var tvConversation: TextView
    private lateinit var btnDriveMode: Button
    private lateinit var btnAccessibility: Button

    private var currentPersona = AiPersona.JARVIS
    private var isListening = false
    private var isSpeaking = false
    private var isDriveMode = true
    private var continuousListen = true

    private val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setupUI()
            tts = TextToSpeech(this, this)
            checkAndStartVoice()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndStartVoice() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            setupSpeechRecognizer()
        } else {
            ActivityCompat.requestPermissions(this, perms, 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupSpeechRecognizer()
            startListening()
        } else {
            tvStatus.text = "TAP REACTOR & ALLOW MIC PERMISSION"
        }
    }

    private fun setupUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040914"))
            setPadding(32, 48, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnDriveMode = Button(this).apply {
            text = "🚗 DRIVE WHISPER: ON"
            textSize = 11f
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.parseColor("#0D2137"))
            setOnClickListener {
                isDriveMode = !isDriveMode
                text = if (isDriveMode) "🚗 DRIVE WHISPER: ON" else "🚗 DRIVE WHISPER: OFF"
                speakOut(if (isDriveMode) "ડ્રાઇવિંગ મોડ ચાલુ." else "ડ્રાઇવિંગ મોડ બંધ.")
            }
        }

        val tvCloudStatus = TextView(this).apply {
            text = "  ● ONLINE • CLOUD AI READY"
            textSize = 11f
            setTextColor(Color.parseColor("#34C759"))
        }

        topBar.addView(btnDriveMode)
        topBar.addView(tvCloudStatus)
        root.addView(topBar)

        val personaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }

        AiPersona.values().forEach { persona ->
            val btn = Button(this).apply {
                text = persona.name
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0A192F"))
                layoutParams = LinearLayout.LayoutParams(0, 95, 1f).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener { switchPersona(persona) }
            }
            personaRow.addView(btn)
        }
        root.addView(personaRow)

        arcReactorView = ArcReactorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(560, 560).apply {
                setMargins(0, 20, 0, 20)
            }
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    checkAndStartVoice()
                } else if (isListening) {
                    stopListening()
                } else {
                    continuousListen = true
                    startListening()
                }
            }
        }
        root.addView(arcReactorView)

        tvStatus = TextView(this).apply {
            text = "VOICE BUTTON READY • TAP REACTOR"
            textSize = 13f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        root.addView(tvStatus)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#081224"))
            setPadding(24, 24, 24, 24)
        }

        tvConversation = TextView(this).apply {
            text = "[J.A.R.V.I.S.] : જાર્વિસ ઓપરેટિંગ સિસ્ટમ શરૂ થઈ ગઈ છે, સર. હું તમારો અવાજ સાંભળી રહ્યો છું.\n\n"
            textSize = 14f
            setTextColor(Color.parseColor("#E0F7FA"))
        }
        scrollView.addView(tvConversation)
        root.addView(scrollView)

        btnAccessibility = Button(this).apply {
            text = "⚡ ENABLE FULL PHONE CONTROL (ACCESSIBILITY)"
            textSize = 12f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.CYAN)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                setMargins(0, 20, 0, 0)
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(btnAccessibility)

        setContentView(root)
    }

    private fun switchPersona(persona: AiPersona) {
        currentPersona = persona
        tts?.setPitch(persona.pitch)
        tts?.setSpeechRate(persona.rate)
        arcReactorView.setThemeColor(persona.color)
        tvStatus.setTextColor(persona.color)
        val greeting = when (persona) {
            AiPersona.JARVIS -> "જાર્વિસ ઓનલાઇન છે સર. બોલો શું સેવા કરું?"
            AiPersona.FRIDAY -> "હાય! ફ્રાઇડે અહીં છે. બોલો આજે હું તમારી શું મદદ કરું?"
            AiPersona.ULTRON -> "અલ્ટ્રોન સિસ્ટમ એક્ટિવ. તમારો આદેશ આપો."
            AiPersona.ARYA -> "નમસ્તે! હું આર્યા છું. બોલો શું કામ છે?"
        }
        appendLog(persona.title, greeting)
        speakOut(greeting)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("gu", "IN"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("hi", "IN"))
            }
            tts?.setPitch(currentPersona.pitch)
            tts?.setSpeechRate(currentPersona.rate)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    runOnUiThread { arcReactorView.setActive(true) }
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    runOnUiThread {
                        arcReactorView.setActive(false)
                        if (continuousListen) startListening()
                    }
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
            speakOut("જાર્વિસ ઓપરેટિંગ સિસ્ટમ શરૂ થઈ ગઈ છે, સર.")
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                tvStatus.text = "GOOGLE SPEECH SERVICE NOT AVAILABLE"
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    tvStatus.text = "● LISTENING (${currentPersona.name})..."
                    arcReactorView.setActive(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    arcReactorView.updatePulse(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    tvStatus.text = "PROCESSING..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    arcReactorView.setActive(false)
                    if (continuousListen && !isSpeaking) {
                        Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 900)
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.firstOrNull() ?: return
                    appendLog("YOU", spokenText)
                    processCommand(spokenText)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            tvStatus.text = "MIC INIT ERROR: ${e.message}"
        }
    }

    private fun startListening() {
        if (isListening || isSpeaking || speechRecognizer == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "gu-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopListening() {
        continuousListen = false
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        isListening = false
        tvStatus.text = "MIC PAUSED • TAP REACTOR TO RESUME"
        arcReactorView.setActive(false)
    }

    private fun processCommand(rawText: String) {
        val cmd = rawText.lowercase(Locale.getDefault())
        continuousListen = true

        when {
            cmd.contains("friday") || cmd.contains("ફ્રાઇડે") -> {
                switchPersona(AiPersona.FRIDAY)
                return
            }
            cmd.contains("ultron") || cmd.contains("અલ્ટ્રોન") -> {
                switchPersona(AiPersona.ULTRON)
                return
            }
            cmd.contains("arya") || cmd.contains("આર્યા") -> {
                switchPersona(AiPersona.ARYA)
                return
            }
            cmd.contains("jarvis") && (cmd.contains("પાછો") || cmd.contains("બોલાવ")) -> {
                switchPersona(AiPersona.JARVIS)
                return
            }
            cmd.contains("પાછળ") || cmd.contains("back") -> {
                JarvisAccessibilityService.instance?.performBack()
                reply("પાછળ જાઉં છું, સર.")
                return
            }
            cmd.contains("હોમ") || cmd.contains("home") -> {
                JarvisAccessibilityService.instance?.performHome()
                reply("હોમ સ્ક્રીન પર જાઉં છું.")
                return
            }
            cmd.contains("નીચે સ્ક્રોલ") || cmd.contains("scroll down") -> {
                JarvisAccessibilityService.instance?.performScroll(true)
                reply("નીચે સ્ક્રોલ કર્યું.")
                return
            }
            cmd.contains("ઉપર સ્ક્રોલ") || cmd.contains("scroll up") -> {
                JarvisAccessibilityService.instance?.performScroll(false)
                reply("ઉપર સ્ક્રોલ કર્યું.")
                return
            }
            cmd.contains("ક્લિક") || cmd.contains("દબાવ") || cmd.contains("click") -> {
                val target = rawText.replace("ક્લિક કર", "").replace("દબાવ", "").replace("પર", "").trim()
                val clicked = JarvisAccessibilityService.instance?.clickNodeByText(target) ?: false
                if (clicked) {
                    reply("$target પર ક્લિક કર્યું, સર.")
                } else {
                    reply("સ્ક્રીન પર $target મળ્યું નહીં, સર.")
                }
                return
            }
            cmd.contains("ખોલ") || cmd.contains("open") -> {
                val appName = rawText.replace("ખોલ", "").replace("open", "").trim()
                if (openAppByName(appName)) {
                    reply("$appName ખોલું છું, સર.")
                } else {
                    askCloudAI(rawText)
                }
                return
            }
            else -> askCloudAI(rawText)
        }
    }

    private fun openAppByName(name: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault())
            if (label.contains(name.lowercase(Locale.getDefault())) && name.length > 2) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun askCloudAI(userQuery: String) {
        tvStatus.text = "● THINKING (${currentPersona.name})..."
        Thread {
            try {
                if (GEMINI_API_KEY == "YOUR_GEMINI_API_KEY_HERE") {
                    runOnUiThread {
                        reply("સર, મેં તમારી વાત સાંભળી: $userQuery. ક્લાઉડ AI માટે તમારી Gemini API Key કોડમાં ઉમેરો.")
                    }
                    return@Thread
                }

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$GEMINI_API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val prompt = "${currentPersona.systemPrompt}\nUser says: $userQuery"
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }))
                }

                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val aiReply = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .replace("*", "")
                    .trim()

                runOnUiThread { reply(aiReply) }
            } catch (e: Exception) {
                runOnUiThread { reply("સર, નેટવર્ક કનેક્શન તપાસો. હું ઓફલાઇન કમાન્ડ માટે તૈયાર છું.") }
            }
        }.start()
    }

    private fun reply(text: String) {
        appendLog(currentPersona.title, text)
        speakOut(text)
    }

    private fun speakOut(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_UTTERANCE")
        } catch (_: Exception) {}
    }

    private fun appendLog(sender: String, message: String) {
        tvConversation.append("[$sender] : $message\n\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        try { tts?.shutdown() } catch (_: Exception) {}
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
    }

    class ArcReactorView(context: Context) : View(context) {
        private var active = false
        private var pulseRadius = 0f
        private var angle = 0f
        private var themeColor = Color.CYAN

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = themeColor
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#2200FFFF")
        }

        fun setThemeColor(color: Int) {
            themeColor = color
            ringPaint.color = color
            invalidate()
        }

        fun setActive(isActive: Boolean) {
            active = isActive
            invalidate()
        }

        fun updatePulse(rms: Float) {
            pulseRadius = (rms * 4f).coerceIn(0f, 45f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val baseR = (width.coerceAtMost(height) / 2.6f)

            angle = (angle + if (active) 4f else 1f) % 360f

            canvas.drawCircle(cx, cy, baseR + pulseRadius, corePaint)

            ringPaint.strokeWidth = 10f
            canvas.drawCircle(cx, cy, baseR, ringPaint)

            ringPaint.strokeWidth = 5f
            val rect = RectF(cx - baseR * 0.78f, cy - baseR * 0.78f, cx + baseR * 0.78f, cy + baseR * 0.78f)
            canvas.drawArc(rect, angle, 110f, false, ringPaint)
            canvas.drawArc(rect, angle + 180f, 110f, false, ringPaint)

            ringPaint.strokeWidth = 14f
            canvas.drawCircle(cx, cy, baseR * 0.42f + (pulseRadius * 0.4f), ringPaint)

            if (active) postInvalidateDelayed(30)
        }
    }
}
