package io.github.zaidbt.tether

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.zaidbt.tether.ui.theme.TetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

// Simple holder for one installed app: display name + unique package ID
// (the package ID is what we'll later use to identify and block an app).
data class AppItem(val label: String, val packageName: String)

// Returns every app that has a launcher icon (i.e. apps the user can open).
// Needs the <queries> block in AndroidManifest.xml, otherwise Android 11+
// returns an empty list because it hides other apps from us.
fun loadLaunchableApps(context: Context): List<AppItem> {
    val pm = context.packageManager

    // Ask the system: "which activities respond to MAIN + LAUNCHER?"
    // That is exactly the set of apps shown in the app drawer.
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    // Android 13 (API 33) introduced a new version of this call and
    // deprecated the old one, so we pick based on the device's Android version.
    val results = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }

    return results
        // Convert each system result into our own AppItem
        .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        // Never list ourselves; we must not be able to block our own app
        .filter { it.packageName != context.packageName }
        // Some apps expose several launcher entries; keep one per package
        .distinctBy { it.packageName }
        // Alphabetical, case-insensitive
        .sortedBy { it.label.lowercase() }
}

// The single screen (Activity) of the app. Android starts this when you open Tether.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status/navigation bars (modern full-screen look)
        enableEdgeToEdge()

        // Everything inside setContent is Jetpack Compose UI
        setContent {
            TetherTheme {
                // Scaffold provides the basic screen layout and tells us how much
                // space the system bars take (innerPadding) so content isn't hidden
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppRoot(Modifier.padding(padding))
                }
            }
        }
    }
}

// The app picker screen: search box, selected counter, and the list of apps.
@Composable
fun AppPickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Load the app list off the main thread
    val apps by produceState(emptyList<AppItem>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var limitText by remember { mutableStateOf("30") } // daily minutes for ticked apps
    var showCode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val db = remember { TetherDatabase.get(context) }

    // Reset the screen to what is saved in the database
    suspend fun loadSaved() {
        val saved = withContext(Dispatchers.IO) { db.blockRuleDao().getAll() }
        selected = saved.map { it.packageName }.toSet()
        saved.firstOrNull()?.let { limitText = it.limitMinutes.toString() }
    }
    LaunchedEffect(Unit) { loadSaved() }

    // Save through the repository; it asks for the code if the change loosens a rule
    fun save() {
        val minutes = limitText.toIntOrNull() ?: 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RuleRepository.save(context, selected.map { BlockRule(it, minutes) })
            }
            if (result == SaveResult.NeedsCode) showCode = true
        }
    }

    if (showCode) {
        PasscodeDialog(
            onSuccess = { showCode = false; save() },
            onDismiss = { showCode = false; scope.launch { loadSaved() } }
        )
    }

    val shown = apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(modifier.padding(horizontal = 16.dp)) {

        // Debug: last detected foreground app
        val fg by ForegroundMonitor.current.collectAsState()
        Text("Foreground: ${fg ?: "none yet"}", style = MaterialTheme.typography.bodySmall)

        // Temporary shortcuts (replaced by the setup checklist)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Accessibility") }
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }) { Text("Usage access") }
            Button(onClick = { PasscodeManager.lock() }) { Text("Lock now") } // temporary test button
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Daily limit applied to every ticked app (0 = always blocked)
        OutlinedTextField(
            value = limitText,
            onValueChange = { limitText = it.filter(Char::isDigit) },
            label = { Text("Daily limit in minutes (0 = block fully)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "${selected.size} selected",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(onClick = { save() }) { Text("Save limits") }

        LazyColumn {
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                AppRow(app, checked) {
                    selected = if (checked) selected - app.packageName
                    else selected + app.packageName
                }
            }
        }
    }
}


// One row in the list: icon, name, package ID, and a checkbox.
@Composable
fun AppRow(app: AppItem, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current

    // Fetch the app's icon and convert it to an image Compose can draw.
    // `remember(app.packageName)` caches it so it isn't reloaded on every redraw.
    val icon = remember(app.packageName) {
        context.packageManager.getApplicationIcon(app.packageName)
            .toBitmap(96, 96).asImageBitmap()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle) // tapping anywhere on the row toggles it
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))

        // weight(1f) makes the text column take all remaining space,
        // pushing the checkbox to the right edge
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

// Shows code setup on first run, then the main screen
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasCode by remember { mutableStateOf(PasscodeManager.isSet(context)) }
    if (hasCode) AppPickerScreen(modifier)
    else SetupPasscodeScreen(modifier) { hasCode = true }
}
