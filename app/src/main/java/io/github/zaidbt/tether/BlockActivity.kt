package io.github.zaidbt.tether

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zaidbt.tether.ui.theme.TetherTheme

// Full-screen page shown when a blocked app is opened
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //back button goes to home so no peeking at the app
        onBackPressedDispatcher.addCallback(this) { goHome() }

        //lookup the blocked app display name
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }

        setContent {
            TetherTheme {
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
                }
            }
        }
    }

    //send user to home and close page
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