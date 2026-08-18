package com.arman.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arman.assistant.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var currentLocale = "bn-BD" // toggled via UI buttons

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled implicitly - CommandProcessor falls back gracefully if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("bn", "BD")
            }
        }

        requestMissingPermissions()

        binding.btnLangBn.setOnClickListener { setLanguage("bn-BD") }
        binding.btnLangEn.setOnClickListener { setLanguage("en-US") }
        binding.btnMic.setOnClickListener { startListening() }
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun setLanguage(locale: String) {
        currentLocale = locale
        val isBn = locale == "bn-BD"
        binding.btnLangBn.setBackgroundColor(
            resources.getColor(if (isBn) R.color.teal else R.color.navy_light, theme)
        )
        binding.btnLangEn.setBackgroundColor(
            resources.getColor(if (!isBn) R.color.teal else R.color.navy_light, theme)
        )
        binding.tvStatus.text = if (isBn) "কথা বলতে ট্যাপ করুন" else "Tap to speak"
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.tvStatus.text = "এই ফোনে ভয়েস রিকগনিশন সাপোর্ট নেই। Voice recognition not supported."
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.tvStatus.text = if (currentLocale == "bn-BD") "শুনছি..." else "Listening..."
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?: return
                    handleRecognizedText(text)
                }

                override fun onError(error: Int) {
                    binding.tvStatus.text = if (currentLocale == "bn-BD") "কথা বলতে ট্যাপ করুন" else "Tap to speak"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleRecognizedText(text: String) {
        binding.tvTranscript.text = text
        val response = CommandProcessor.process(text, applicationContext)
        binding.tvResponse.text = response
        binding.tvStatus.text = if (currentLocale == "bn-BD") "কথা বলতে ট্যাপ করুন" else "Tap to speak"
        speak(response)
    }

    private fun speak(text: String) {
        val looksBangla = text.any { it.code in 0x0980..0x09FF }
        tts?.language = if (looksBangla) Locale("bn", "BD") else Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "arman_reply")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
