package br.com.riseedit.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
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
import java.io.File
import kotlinx.coroutines.delay

/** Voice-over: records AAC into app storage and inserts it at the playhead. */
@Composable
internal fun RecordPanel(addAudio: (Uri) -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    fun start() {
        val target = File(context.filesDir, "recordings").apply { mkdirs() }
            .resolve("narracao-${System.currentTimeMillis()}.m4a")
        error = runCatching {
            @Suppress("DEPRECATION")
            val created = if (Build.VERSION.SDK_INT >=
                31
            ) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = created
            file = target
            started = SystemClock.elapsedRealtime()
        }.exceptionOrNull()?.let { "Não foi possível gravar: ${it.message}" }
    }
    fun stop() {
        val active = recorder ?: return
        recorder = null
        val ok = runCatching { active.stop() }.isSuccess
        active.release()
        file?.takeIf { ok && it.length() > 0 }?.let { addAudio(Uri.fromFile(it)) }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) start() else error = "Permita o microfone para gravar."
        }
    LaunchedEffect(recorder) {
        while (recorder != null) {
            elapsed = SystemClock.elapsedRealtime() - started
            delay(200)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }
    val recording = recorder != null
    Text(
        if (recording) {
            "Gravando · ${time(
                elapsed
            )}"
        } else {
            "A narração entra no cursor, na faixa de áudio."
        },
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
