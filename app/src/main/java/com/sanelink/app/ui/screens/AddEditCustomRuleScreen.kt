package com.sanelink.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.ui.components.AutoFitTitleText
import com.sanelink.app.ui.components.ExpressiveDualActionButtonBar
import com.sanelink.app.ui.components.ExpressiveTactileButton
import androidx.compose.foundation.layout.PaddingValues
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCustomRuleScreen(
    ruleToEdit: CustomRule?,
    onBack: () -> Unit,
    onSave: (CustomRule) -> Unit,
    onPatchBuiltIn: (String) -> Unit = {}
) {
    var name by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.name ?: "") }
    var urlPattern by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.urlPattern ?: "") }
    var stripParams by remember(ruleToEdit) {
        mutableStateOf(ruleToEdit?.stripParameters?.joinToString(", ") ?: "")
    }
    var rewritePattern by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.rewritePattern ?: "") }
    var rewriteReplacement by remember(ruleToEdit) { mutableStateOf(ruleToEdit?.rewriteReplacement ?: "") }

    val clearUrlsData by SaneLinkApp.instance.repository.clearUrlsData.collectAsState()
    var dismissedConflictProvider by remember { mutableStateOf<String?>(null) }

    val matchingProvider = remember(name, urlPattern, clearUrlsData, dismissedConflictProvider) {
        if (urlPattern.isBlank() && name.isBlank()) return@remember null

        val cleanPattern = urlPattern.trim().lowercase()
        val cleanName = name.trim().lowercase()

        val matched = clearUrlsData.providers.entries.firstOrNull { (providerId, providerRule) ->
            // Do not suggest catch-all global rules (e.g. globalRules) as duplicates for specific custom rules
            if (providerId.equals("globalRules", ignoreCase = true) || providerRule.urlPattern == ".*") {
                return@firstOrNull false
            }

            val pId = providerId.lowercase()
            (cleanName.isNotBlank() && (cleanName == pId || (pId.length >= 3 && cleanName.contains(pId)))) ||
            (cleanPattern.isNotBlank() && (
                cleanPattern.contains(pId) ||
                cleanPattern.contains("$pId.") ||
                try {
                    providerRule.urlPattern.isNotBlank() &&
                        providerRule.urlPattern != ".*" &&
                        Regex(providerRule.urlPattern, RegexOption.IGNORE_CASE).containsMatchIn(urlPattern)
                } catch (_: Exception) { false }
            ))
        }

        if (matched != null && matched.key != dismissedConflictProvider) {
            matched.key
        } else {
            null
        }
    }

    val isEditing = ruleToEdit != null
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSave = name.isNotBlank() && urlPattern.isNotBlank()

    val onSaveAction = {
        if (canSave) {
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
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val titleText = if (isEditing) "Edit Custom Rule" else "Add Custom Rule"
                    AutoFitTitleText(
                        text = titleText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ExpressiveDualActionButtonBar(
                primaryText = if (isEditing) "Update Rule" else "Save Custom Rule",
                secondaryText = "Close",
                primaryEnabled = canSave,
                onPrimaryClick = onSaveAction,
                onSecondaryClick = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imeNestedScroll()
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

            // Smart built-in duplicate suggestion banner
            AnimatedVisibility(
                visible = matchingProvider != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                matchingProvider?.let { matchedId ->
                    BuiltInMatchBanner(
                        providerName = formatProviderName(matchedId),
                        onPatch = { onPatchBuiltIn(matchedId) },
                        onKeep = { dismissedConflictProvider = matchedId },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            // Rule Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g. Reddit to Redlib") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // URL Pattern
            OutlinedTextField(
                value = urlPattern,
                onValueChange = { urlPattern = it },
                label = { Text("URL / Domain Pattern (Regex)") },
                placeholder = { Text("e.g. ^https?://(?:www\\.)?reddit\\.com") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Parameters to strip
            OutlinedTextField(
                value = stripParams,
                onValueChange = { stripParams = it },
                label = { Text("Parameters to Strip (comma-separated)") },
                placeholder = { Text("e.g. utm_source, share_id, rdt") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
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
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rewrite replacement
            OutlinedTextField(
                value = rewriteReplacement,
                onValueChange = { rewriteReplacement = it },
                label = { Text("Frontend Replacement Target") },
                placeholder = { Text("e.g. https://redlib.catsarch.com/$1") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) keyboardController?.show() }
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

/**
 * Suggests patching the existing built-in rule instead of creating a duplicate custom rule.
 *
 * Uses solid container colours (no alpha blending) so it stays vivid and readable on every
 * palette, including dark themes and devices without dynamic colour.
 */
@Composable
private fun BuiltInMatchBanner(
    providerName: String,
    onPatch: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_auto_fix_high),
                        contentDescription = null,
                        tint = scheme.onTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$providerName is already built in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onTertiaryContainer
                    )
                    Text(
                        text = "Suggestion",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Patching keeps SaneLink's tracker list for this site, and you can still add your own parameters or open links on a different site.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpressiveTactileButton(
                    onClick = onPatch,
                    filled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.tertiary,
                        contentColor = scheme.onTertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text("Patch built-in", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                ExpressiveTactileButton(
                    onClick = onKeep,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = scheme.surfaceContainerLowest,
                        contentColor = scheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Keep custom", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
