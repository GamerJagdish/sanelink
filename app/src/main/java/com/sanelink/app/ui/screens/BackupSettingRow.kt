package com.sanelink.app.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.backup.BackupCodec
import com.sanelink.app.core.backup.BackupParseResult
import com.sanelink.app.core.backup.SaneLinkBackup
import com.sanelink.app.service.ClipboardCleaner
import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.components.ExpressiveTactileButton
import com.sanelink.app.ui.theme.GoogleSansCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Backup" item for the Settings page, with Export and Import buttons. A backup can go out as
 * text (copied to the clipboard) or a .json file, and come back the same two ways.
 */
@Composable
fun BackupSettingRow(index: Int, count: Int) {
    val context = LocalContext.current
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()

    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SaneLinkBackup?>(null) }

    fun handleParsed(result: BackupParseResult) {
        when (result) {
            is BackupParseResult.Success -> pending = result.backup
            is BackupParseResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = writeBackupTo(context, uri, BackupCodec.encode(app.preferences.exportBackup()))
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = readBackupFrom(context, uri)
            if (text == null) {
                Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            } else {
                handleParsed(BackupCodec.decode(text))
            }
        }
    }

    ExpressiveSegmentedListItem(
        index = index,
        count = count,
        leading = { ExpressiveLeadingBadge(icon = painterResource(R.drawable.ic_backup)) },
        headline = {
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supporting = {
            Column {
                Text(
                    text = "Custom rules, patches and preferences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                BoxWithConstraints {
                    // Icons only when there's room for them next to the words
                    val showIcons = maxWidth >= 220.dp
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupButton("Export", R.drawable.ic_upload, showIcons, { showExport = true }, Modifier.weight(1f))
                        BackupButton("Import", R.drawable.ic_download, showIcons, { showImport = true }, Modifier.weight(1f))
                    }
                }
            }
        }
    )

    if (showExport) {
        ChoiceDialog(
            title = "Export",
            text = "Recent links aren't included.",
            first = "Copy as text",
            second = "Save to file",
            onFirst = {
                showExport = false
                scope.launch {
                    // Written as SaneLink's own clipboard content, so auto-clean won't touch it
                    ClipboardCleaner.writeOwnClip(context, BackupCodec.encode(app.preferences.exportBackup()))
                    Toast.makeText(context, "Backup copied. Paste it somewhere safe.", Toast.LENGTH_SHORT).show()
                }
            },
            onSecond = {
                showExport = false
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                saveFile.launch("sanelink-backup-$date.json")
            },
            onDismiss = { showExport = false }
        )
    }

    if (showImport) {
        ChoiceDialog(
            title = "Import",
            text = "You'll see what's in the backup before anything changes.",
            first = "Paste text",
            second = "Choose file",
            onFirst = { showImport = false; showPaste = true },
            onSecond = {
                showImport = false
                openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            onDismiss = { showImport = false }
        )
    }

    if (showPaste) {
        PasteBackupDialog(
            onImport = { text -> showPaste = false; handleParsed(BackupCodec.decode(text)) },
            onDismiss = { showPaste = false }
        )
    }

    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Import this backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "It has ${BackupCodec.summary(backup)}.\n\n" +
                        "Add to mine keeps your current rules and adds or updates the ones in the backup. " +
                        "Replace all makes the backup your whole setup, including preferences."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        app.preferences.importBackup(backup, replace = false)
                        Toast.makeText(context, "Backup added", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Add to mine", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        app.preferences.importBackup(backup, replace = true)
                        Toast.makeText(context, "Setup replaced from backup", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Replace all", color = MaterialTheme.colorScheme.error) }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

@Composable
private fun BackupButton(text: String, icon: Int, showIcon: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExpressiveTactileButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier
    ) {
        if (showIcon) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Dialog offering two ways to do something, as two tactile buttons. */
@Composable
private fun ChoiceDialog(
    title: String,
    text: String,
    first: String,
    second: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveTactileButton(
                        onClick = onFirst,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(first, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    ExpressiveTactileButton(
                        onClick = onSecond,
                        filled = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(second, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
private fun PasteBackupDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste backup", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("{ \"format\": \"sanelink-backup\", … }") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansCode),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                )
                ExpressiveTactileButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        text = cm?.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Paste from clipboard", fontWeight = FontWeight.SemiBold) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) {
                Text("Import", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

private suspend fun writeBackupTo(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
    }.getOrDefault(false)
}

private suspend fun readBackupFrom(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
}
