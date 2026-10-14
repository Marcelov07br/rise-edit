package br.com.riseedit.tools

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseWhite
import br.com.riseedit.time
import kotlinx.coroutines.delay

/** Voice-over: records AAC into app storage and inserts it at the playhead. */
@Composable
internal fun RecordPanel(addAudio: (Uri) -> Unit) {
    val context = LocalContext.current
    val latestAdd = androidx.compose.runtime.rememberUpdatedState(addAudio)
    val voice = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    fun start() {
        error = voice.start()
        recording = voice.recording
    }
    fun finish(result: VoiceRecorder.Result?) {
        recording = voice.recording
        when (result) {
            is VoiceRecorder.Result.Saved -> latestAdd.value(Uri.fromFile(result.file))
            is VoiceRecorder.Result.Failed -> error = result.message
            null -> Unit
        }
    }
    fun stop() = finish(voice.stop())
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            br.com.riseedit.diagnostics.Diagnostics.log("gravação", "permissão do microfone: $it")
            if (it) start() else error = "Permita o microfone para gravar."
        }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = SystemClock.elapsedRealtime() - voice.startedAt
            delay(200)
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // Leaving the app keeps what was recorded: stop, finalize and insert it.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && voice.recording) finish(voice.release())
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Closing the panel also inserts the recording; release never throws.
            finish(voice.release())
        }
    }
    Text(
        if (recording) "Gravando · ${time(elapsed)}" else "A narração entra no cursor, na faixa de áudio.",
        style = MaterialTheme.typography.bodySmall
    )
    Button(
        onClick = {
            when {
                recording -> stop()
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> start()
                else -> permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (recording) {
                androidx.compose.ui.graphics.Color(
                    0xFFFF3B30
                )
            } else {
                RiseWhite
            },
            contentColor = if (recording) RiseWhite else RiseBlack
        )
    ) { Text(if (recording) "Parar e inserir" else "Gravar") }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
