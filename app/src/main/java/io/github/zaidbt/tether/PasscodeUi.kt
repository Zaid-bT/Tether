package io.github.zaidbt.tether

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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}