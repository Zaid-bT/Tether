//the masked input field, the first-run setup screen, the code prompt, and the forgot-code dialog.
//It contains no lock logic of its own and only calls PasscodeManager.

package io.github.zaidbt.tether.passcode

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
@Composable  //Composable marks a function that describes UI
fun CodeField(value: String, onChange: (String) -> Unit, label: String) {  //state hoisting: CodeField doesnt hold the string
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },  //runs at every keystroke(types,pasted and/or deleted)
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),  //PasswordVisualTransformation(): shows dots instead of digits
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),  //NumberPassword brings up a numeric keyboard that hides what you type
        modifier = Modifier.fillMaxWidth()
    )
}

// First-run screen
@Composable
fun SetupPasscodeScreen(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val context = LocalContext.current  //grabs the Android context needed by PasscodeManager(SharedPreference)
    val scope = rememberCoroutineScope()  //provides a Coroutine scope that allows launching suspend calls (setPasscode in PasscodeManager); suspend calls cannot be called directly with onClock without context
    var code by remember { mutableStateOf("") }  //remember keeps the state alive across recomposition/re-run, instead of resetting "" everytime
    var confirm by remember { mutableStateOf("") }  //holds the "confirm passcode" input string
    var error by remember { mutableStateOf<String?>(null) }  //codes dont match/short passcode

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

// Popup asking for the code before lowering time limit
@Composable
fun PasscodeDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }  //error msg
    var showForgot by remember { mutableStateOf(false) }

    //if showForgot is true, show the forgot code dialog instead
    if (showForgot) {
        ForgotCodeDialog(onClose = { showForgot = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,  //when user taps outside or presses back
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
                TextButton(onClick = { showForgot = true }) { Text("Forgot code") }  //sets showForgot true for next run
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
    var remaining by remember { mutableStateOf(PasscodeManager.resetRemainingMs(context)) }  //holds the time left on the wait
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Re-show the countdown notification once permission is granted (Android 13+) ("allow notification?" prompt)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        remaining?.takeIf { it > 0 } //keep value only when positive
            ?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
    }

    // Refresh the countdown every second while the dialog is open
    LaunchedEffect(Unit) { //runs when dialog appears
        remaining?.takeIf { it > 0 }
            ?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
        while (true) {
            delay(1000)  //refresh every second
            remaining = PasscodeManager.resetRemainingMs(context)
        }
    }

    // h:mm:ss text for the countdown
    fun format(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    val left = remaining  //copies state into local value
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
            //button changes with the state
            when {
                left == null -> TextButton(onClick = {  //no wait time set: start it and post notification
                    PasscodeManager.startReset(context)
                    remaining = PasscodeManager.resetRemainingMs(context)
                    remaining?.let { ResetNotifier.show(context, System.currentTimeMillis() + it) }
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Start 12-hour wait") }

                left > 0 -> TextButton(onClick = {  //wait time currently running
                    PasscodeManager.cancelReset(context)
                    remaining = null
                }) { Text("Cancel reset") }

                else -> TextButton(onClick = {  //wait time is over
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