package com.sanelink.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.core.model.CustomRule

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCustomRuleScreen(
    ruleToEdit: CustomRule?,
    onBack: () -> Unit,
    onSave: (CustomRule) -> Unit
) {
    var name by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.name ?: "") }
    var urlPattern by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.urlPattern ?: "") }
    var stripParams by remember(ruleToEdit) {
        mutableStateOf(ruleToEdit?.stripParameters?.joinToString(", ") ?: "")
    }
    var rewritePattern by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.rewritePattern ?: "") }
    var rewriteReplacement by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.rewriteReplacement ?: "") }

    val isEditing = ruleToEdit != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Custom Rule" else "Add Custom Rule",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                ElevatedButton(
                    onClick = {
                        if (name.isNotBlank() && urlPattern.isNotBlank()) {
                            val parsedParams = stripParams.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val rule = CustomRule(
                                id = ruleToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                urlPattern = urlPattern.trim(),
                                stripParameters = parsedParams,
                                rewritePattern = rewritePattern.trim().ifBlank { null },
                                rewriteReplacement = rewriteReplacement.trim().ifBlank { null },
                                isEnabled = ruleToEdit?.isEnabled ?: true
                            )
                            onSave(rule)
                        }
                    },
                    enabled = name.isNotBlank() && urlPattern.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = if (isEditing) "Update Rule" else "Save Custom Rule",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Quick presets
            Text(
                text = "QUICK PRESETS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        name = "Reddit to Redlib"
                        urlPattern = "^https?://(?:www\\.)?reddit\\.com"
                        stripParams = "utm_source, share_id, rdt, correlation_id"
                        rewritePattern = "^https?://(?:www\\.)?reddit\\.com/(.*)"
                        rewriteReplacement = "https://redlib.catsarch.com/$1"
                    },
                    label = { Text("Reddit → Redlib") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        name = "X to FixupX"
                        urlPattern = "^https?://(?:www\\.)?(?:twitter\\.com|x\\.com)"
                        stripParams = "s, t, ref_src, ref_url"
                        rewritePattern = "^https?://(?:www\\.)?(?:twitter\\.com|x\\.com)/(.*)"
                        rewriteReplacement = "https://fixupx.com/$1"
                    },
                    label = { Text("X/Twitter → FixupX") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        name = "YouTube to Invidious"
                        urlPattern = "^https?://(?:www\\.)?youtube\\.com"
                        stripParams = "si, feature, pp"
                        rewritePattern = "^https?://(?:www\\.)?youtube\\.com/(.*)"
                        rewriteReplacement = "https://yewtu.be/$1"
                    },
                    label = { Text("YouTube → Invidious") }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rule Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g. Reddit to Redlib") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // URL Pattern
            OutlinedTextField(
                value = urlPattern,
                onValueChange = { urlPattern = it },
                label = { Text("URL / Domain Pattern (Regex)") },
                placeholder = { Text("e.g. ^https?://(?:www\\.)?reddit\\.com") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Parameters to strip
            OutlinedTextField(
                value = stripParams,
                onValueChange = { stripParams = it },
                label = { Text("Parameters to Strip (comma-separated)") },
                placeholder = { Text("e.g. utm_source, share_id, rdt") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "FRONTEND / URL REWRITE (OPTIONAL)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Rewrite match pattern
            OutlinedTextField(
                value = rewritePattern,
                onValueChange = { rewritePattern = it },
                label = { Text("Frontend Match Regex") },
                placeholder = { Text("e.g. ^https?://(?:www\\.)?reddit\\.com/(.*)") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rewrite replacement
            OutlinedTextField(
                value = rewriteReplacement,
                onValueChange = { rewriteReplacement = it },
                label = { Text("Frontend Replacement Target") },
                placeholder = { Text("e.g. https://redlib.catsarch.com/$1") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
