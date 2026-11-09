package com.sanelink.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.SanitizationStats
import com.sanelink.app.ui.components.ExpressiveFloatingButton
import com.sanelink.app.ui.components.ExpressiveSandboxCard
import com.sanelink.app.ui.components.ExpressiveStatsRow
import com.sanelink.app.ui.components.HeroProtectionCard
import com.sanelink.app.ui.components.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val app = SaneLinkApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current

    val isAutoSanitize by app.preferences.autoSanitizeEnabled.collectAsState(initial = true)
    val stats by app.preferences.stats.collectAsState(initial = SanitizationStats())

    var sandboxInput by remember { mutableStateOf("") }
    val sanitizer = remember(app.repository.clearUrlsData, app.repository.customRules, app.repository.disabledProviderIds) {
        app.repository.getSanitizer()
    }

    val sandboxResult = remember(sandboxInput, sanitizer) {
        if (sandboxInput.isNotBlank()) sanitizer.sanitize(sandboxInput) else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Card
            HeroProtectionCard(
                isEnabled = isAutoSanitize,
                onToggle = { enabled ->
                    scope.launch { app.preferences.setAutoSanitize(enabled) }
                }
            )

            // Metrics Summary
            ExpressiveStatsRow(stats = stats)

            // Test & Clean Sandbox
            ExpressiveSandboxCard(
                inputText = sandboxInput,
                onInputChanged = { sandboxInput = it },
                result = sandboxResult,
                onCopyClean = { cleanUrl ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("sanitized_url", cleanUrl))
                    Toast.makeText(context, "Clean URL copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            // Bottom blank area so content scrolls cleanly past floating button and navbar
            Spacer(modifier = Modifier.height(200.dp))
        }

        // Material 3 Expressive Floating "Paste Link" button with spring physics and frosted blur
        ExpressiveFloatingButton(
            text = "Paste Link",
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotBlank()) {
                        sandboxInput = text.trim()
                        Toast.makeText(context, "Link pasted from clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
                .height(52.dp)
        )
    }
}
