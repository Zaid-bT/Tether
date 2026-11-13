package io.github.zaidbt.tether

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Forced wait, then a typed reason, then the unlock is granted
@Composable
fun EmergencyDialog(pkg: String, onGranted: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var secondsLeft by remember { mutableIntStateOf(EmergencyManager.WAIT_SECONDS) }
    var reason by remember { mutableStateOf("") }

    // Count down once per second
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val minutes = EmergencyManager.GRANT_MS / 60_000
    val canConfirm = secondsLeft == 0 && reason.trim().length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency unlock") },
        text = {
            Column {
                Text("The app opens for $minutes minutes, then is blocked again.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why do you need it?") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (secondsLeft > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Wait ${secondsLeft}s...", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    scope.launch {
                        EmergencyManager.grant(context, pkg, reason.trim())
                        onGranted()
                    }
                }
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}