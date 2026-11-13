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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.ui.components.AutoFitTitleText
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
            val pId = providerId.lowercase()
            (cleanName.isNotBlank() && (cleanName == pId || cleanName.contains(pId))) ||
            (cleanPattern.isNotBlank() && (
                cleanPattern.contains(pId) ||
                cleanPattern.contains("$pId.") ||
                try { Regex(providerRule.urlPattern, RegexOption.IGNORE_CASE).containsMatchIn(urlPattern) } catch (_: Exception) { false }
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
            val navBarBottom = WindowInsets.navigationBars
                .exclude(WindowInsets.ime)
                .asPaddingValues()
                .calculateBottomPadding()

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = navBarBottom)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    val saveInteractionSource = remember { MutableInteractionSource() }
                    val isSavePressed by saveInteractionSource.collectIsPressedAsState()

                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isClosePressed by closeInteractionSource.collectIsPressedAsState()
                    val motionScheme = LocalMotionScheme.current

                    // M3 Expressive Spring Width Physics (70% Save / 30% Close baseline with 12% neighbor compression on touch)
                    val saveTargetWeight = when {
                        isSavePressed && canSave -> 0.82f
                        isClosePressed -> 0.58f
                        else -> 0.70f
                    }
                    val closeTargetWeight = when {
                        isClosePressed -> 0.42f
                        isSavePressed && canSave -> 0.18f
                        else -> 0.30f
                    }
                    val animatedSaveWeight by animateFloatAsState(
                        targetValue = saveTargetWeight,
                        animationSpec = motionScheme.fastSpatial,
                        label = "saveWeight"
                    )
                    val animatedCloseWeight by animateFloatAsState(
                        targetValue = closeTargetWeight,
                        animationSpec = motionScheme.fastSpatial,
                        label = "closeWeight"
                    )

                    // Tactile press scale feedback (squish & bounce)
                    val savePressScale by animateFloatAsState(
                        targetValue = if (isSavePressed && canSave) 0.96f else 1f,
                        animationSpec = motionScheme.fastSpatial,
                        label = "savePressScale"
                    )
                    val closePressScale by animateFloatAsState(
                        targetValue = if (isClosePressed) 0.96f else 1f,
                        animationSpec = motionScheme.fastSpatial,
                        label = "closePressScale"
                    )

                    // Color transitions (Effects springs: critical damping, no overshoot)
                    val saveBgColor by animateColorAsState(
                        targetValue = if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                        animationSpec = motionScheme.defaultColorEffects,
                        label = "saveBgColor"
                    )
                    val saveContentColor by animateColorAsState(
                        targetValue = if (canSave) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        animationSpec = motionScheme.fastColorEffects,
                        label = "saveContentColor"
                    )

                    // Dynamic M3 Shape Morphing: transforms into standalone rounded pill on press / drag
                    val fullRadius = 24.dp
                    val innerRadius = 6.dp

                    val saveEndRadius by animateDpAsState(
                        targetValue = if (isSavePressed && canSave) fullRadius else innerRadius,
                        animationSpec = spring(
                            dampingRatio = motionScheme.fastSpatial.dampingRatio,
                            stiffness = motionScheme.fastSpatial.stiffness
                        ),
                        label = "saveEndRadius"
                    )
                    val closeStartRadius by animateDpAsState(
                        targetValue = if (isClosePressed) fullRadius else innerRadius,
                        animationSpec = spring(
                            dampingRatio = motionScheme.fastSpatial.dampingRatio,
                            stiffness = motionScheme.fastSpatial.stiffness
                        ),
                        label = "closeStartRadius"
                    )

                    val saveShape = RoundedCornerShape(
                        topStart = fullRadius,
                        bottomStart = fullRadius,
                        topEnd = saveEndRadius,
                        bottomEnd = saveEndRadius
                    )
                    val closeShape = RoundedCornerShape(
                        topStart = closeStartRadius,
                        bottomStart = closeStartRadius,
                        topEnd = fullRadius,
                        bottomEnd = fullRadius
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Main Save / Update Button (70% width)
                        Box(
                            modifier = Modifier
                                .weight(animatedSaveWeight)
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = savePressScale
                                    scaleY = savePressScale
                                }
                                .clip(saveShape)
                                .background(saveBgColor)
                                .clickable(
                                    enabled = canSave,
                                    interactionSource = saveInteractionSource,
                                    indication = ripple()
                                ) {
                                    onSaveAction()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEditing) "Update Rule" else "Save Custom Rule",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = saveContentColor
                            )
                        }

                        // Close Button (30% width)
                        Box(
                            modifier = Modifier
                                .weight(animatedCloseWeight)
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = closePressScale
                                    scaleY = closePressScale
                                }
                                .clip(closeShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(
                                    interactionSource = closeInteractionSource,
                                    indication = ripple()
                                ) {
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Close",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_info),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Built-in rule for ${formatProviderName(matchedId)} exists",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "SaneLink already has built-in tracking rules for this site. You can patch the built-in rule instead, or keep this custom rule (Custom rules are evaluated first with Priority 1).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ElevatedButton(
                                    onClick = { onPatchBuiltIn(matchedId) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.elevatedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Patch Built-in Rule",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { dismissedConflictProvider = matchedId },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Keep Independent",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
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
                modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
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
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

private fun formatProviderName(key: String): String {
    return key.split("_", "-", " ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
