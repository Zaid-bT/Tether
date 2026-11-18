package io.github.zaidbt.tether

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.zaidbt.tether.ui.theme.TetherTheme

// Passcode prompt shown over a protected settings screen
class GuardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            TetherTheme {
                Box(Modifier.fillMaxSize())
                // Correct code opens the 5-minute session; anything else goes home
                PasscodeDialog(onSuccess = { finish() }, onDismiss = { goHome() })
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}