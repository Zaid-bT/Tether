package io.github.zaidbt.tether

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zaidbt.tether.ui.theme.TetherTheme

// Full-screen page shown when a blocked app is opened
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back button also goes home, so it can't be used to peek at the app
        onBackPressedDispatcher.addCallback(this) { goHome() }

        // Look up the blocked app's display name
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        setContent {
            TetherTheme {
                var usesLeft by remember { mutableIntStateOf(0) }
                var showDialog by remember { mutableStateOf(false) }

                // Check how many emergency unlocks remain today
                LaunchedEffect(Unit) { usesLeft = EmergencyManager.usesLeft(applicationContext) }

                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$appName is blocked", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Your limit for this app has been reached.")
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { goHome() }) { Text("Go home") }

                    // Only offered while unlocks remain today
                    if (usesLeft > 0) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showDialog = true }) {
                            Text("Emergency unlock ($usesLeft left today)")
                        }
                    }
                }

                if (showDialog) {
                    EmergencyDialog(
                        pkg = pkg,
                        onGranted = { openApp(pkg) },
                        onDismiss = { showDialog = false }
                    )
                }
            }
        }
    }

    // Reopen the app that was blocked, then close this page
    private fun openApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        finish()
    }

    // Send the user to the home screen and close this page
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
    }
}