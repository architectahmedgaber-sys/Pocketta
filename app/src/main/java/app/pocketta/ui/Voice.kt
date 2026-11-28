package app.pocketta.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.pocketta.i18n.Tr

/**
 * Voice entry for the add sheet. The words are only put into the one-line field; the person still taps Save.
 * It uses ONLY on-device speech recognition (Android 13+ with a speech pack installed): no audio is stored or sent.
 * If the phone cannot do that, [onMessage] says so instead of quietly using an online service.
 * Returns a function that starts listening.
 */
@Composable
fun rememberVoiceInput(lang: String, onText: (String) -> Unit, onMessage: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<SpeechRecognizer>(1) }
    DisposableEffect(Unit) { onDispose { holder[0]?.destroy(); holder[0] = null } }

    fun listen(c: Context) {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(c)) {
            onMessage("Voice entry needs speech recognition that works on the phone itself, and this phone does not have it. Nothing was recorded or sent. Type instead.")
            return
        }
        holder[0]?.destroy()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(c)
        holder[0] = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onMessage("Listening...") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                onMessage(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Did not hear anything. Tap the microphone and try again."
                        12, 13 -> "This language is not installed for offline speech on this phone. Type instead."
                        else -> "Voice entry did not work. Type instead."
                    }
                )
                r.destroy(); holder[0] = null
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onMessage("Did not hear anything. Tap the microphone and try again.") else { onMessage(""); onText(text) }
                r.destroy(); holder[0] = null
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == Tr.AR) "ar-EG" else "en-US")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        )
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen(context) else onMessage("Allow the microphone to use voice entry, or type instead.")
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen(context)
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
