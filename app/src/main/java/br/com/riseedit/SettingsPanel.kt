@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import br.com.riseedit.core.*
import java.util.Locale

@Composable
fun UpdateSettings(vm: UpdateViewModel, state: UpdateState, canInstall: Boolean, install: () -> Unit, close: () -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Configurações") }, confirmButton = { TextButton(onClick = close) { Text("Fechar") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Rise Edit ${state.installedVersion} · Atualizações pelo GitHub", fontWeight = FontWeight.Bold)
                Text(UpdatePolicy.REPOSITORY, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Buscar automaticamente ao abrir", modifier = Modifier.weight(1f))
                    Switch(checked = state.automatic, onCheckedChange = vm::automatic)
                }
                Text("Repositório privado: use um token fine-grained, limitado a rise-edit, com Contents: Read-only. A credencial fica criptografada no aparelho.", style = MaterialTheme.typography.bodySmall)
                if (!state.connected) {
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token GitHub") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                    Button(onClick = { vm.connect(token); token = "" }, enabled = token.isNotBlank() && !state.busy) { Text("Conectar GitHub") }
                } else TextButton(onClick = vm::disconnect, enabled = !state.busy) { Text("Desconectar GitHub") }
                OutlinedButton(onClick = vm::check, enabled = !state.busy) { Text("Buscar atualização agora") }
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.busy) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                state.release?.let { release ->
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                    if (state.ready == null) Button(onClick = vm::download, enabled = !state.busy) { Text("Baixar ${release.tag}") }
                }
                if (state.ready != null) Button(onClick = install, enabled = canInstall) { Text("Instalar atualização") }
                Text("A primeira instalação usa um APK. As seguintes são baixadas aqui. O Android exige autorização para instalar; depois de concedê-la, toque em instalar novamente.", style = MaterialTheme.typography.bodySmall)
            }
        })
}
