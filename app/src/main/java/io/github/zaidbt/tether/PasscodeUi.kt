package io.github.zaidbt.tether

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Masked numeric field used for entering codes
@Composable
fun CodeField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

// First-run screen: a trusted person picks the code
@Composable
fun SetupPasscodeScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hand the phone to someone you trust", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("They choose a code (at least 4 digits) that you won't see. It is needed to raise or remove limits.")
        Spacer(Modifier.height(16.dp))
        CodeField(code, { code = it }, "New code")
        Spacer(Modifier.height(8.dp))
        CodeField(confirm, { confirm = it }, "Repeat code")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            when {
                code.length < 4 -> error = "Use at least 4 digits"
                code != confirm -> error = "Codes don't match"
                else -> scope.launch {
                    PasscodeManager.setPasscode(context, code)
                    onDone()
                }
            }
        }) { Text("Set code") }
    }
}

// Popup asking for the code before a loosening change
@Composable
fun PasscodeDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }

    // Forgot-code flow replaces the prompt while it is open
    if (showForgot) {
        ForgotCodeDialog(onClose = { showForgot = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter passcode") },
        text = {
            Column {
                CodeField(code, { code = it }, "Passcode")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val r = PasscodeManager.verify(context, code)) {
                        VerifyResult.Ok -> onSuccess()
                        is VerifyResult.Wrong -> { message = "Wrong code. ${r.attemptsLeft} tries left."; code = "" }
                        is VerifyResult.LockedOut -> { message = "Too many tries. Wait ${r.secondsLeft}s."; code = "" }
                    }
                }
            }) { Text("Unlock") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showForgot = true }) { Text("Forgot code") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// Start, watch, cancel and finish the 12-hour code reset
@Composable
fun ForgotCodeDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var remaining by remember { mutableStateOf(PasscodeManager.resetRemainingMs(context)) }
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Re-show the countdown notification once permission is granted (Android 13+)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        remaining?.takeIf { it > 0 }
            ?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
    }

    // Refresh the countdown every second while the dialog is open
    LaunchedEffect(Unit) {
        remaining?.takeIf { it > 0 }
            ?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
        while (true) {
            delay(1000)
            remaining = PasscodeManager.resetRemainingMs(context)
        }
    }

    // h:mm:ss text for the countdown
    fun format(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    val left = remaining
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Forgot code") },
        text = {
            Column {
                when {
                    left == null -> Text(
                        "A new code can be set after a 12-hour wait. Entering the current code before then cancels the reset."
                    )
                    left > 0 -> Text("A new code can be set in ${format(left)}.")
                    else -> {
                        CodeField(code, { code = it }, "New code")
                        Spacer(Modifier.height(8.dp))
                        CodeField(confirm, { confirm = it }, "Repeat code")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                left == null -> TextButton(onClick = {
                    PasscodeManager.startReset(context)
                    remaining = PasscodeManager.resetRemainingMs(context)
                    remaining?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Start 12-hour wait") }

                left > 0 -> TextButton(onClick = {
                    PasscodeManager.cancelReset(context)
                    remaining = null
                }) { Text("Cancel reset") }

                else -> TextButton(onClick = {
                    when {
                        code.length < 4 -> error = "Use at least 4 digits"
                        code != confirm -> error = "Codes don't match"
                        else -> scope.launch {
                            if (PasscodeManager.completeReset(context, code)) onClose()
                        }
                    }
                }) { Text("Set new code") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Back") } }
    )
}