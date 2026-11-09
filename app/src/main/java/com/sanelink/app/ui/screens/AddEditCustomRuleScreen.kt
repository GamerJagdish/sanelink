package com.sanelink.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.ui.theme.GoogleSansCode

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
                    Text(
                        text = if (isEditing) "Edit Custom Rule" else "Add Custom Rule",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
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

                    // M3 Expressive Spring Width Physics (70% Save / 30% Close baseline with neighbor compression)
                    val saveTargetWeight = when {
                        isSavePressed && canSave -> 0.74f
                        isClosePressed -> 0.65f
                        else -> 0.70f
                    }
                    val closeTargetWeight = when {
                        isClosePressed -> 0.35f
                        isSavePressed && canSave -> 0.26f
                        else -> 0.30f
                    }
                    val animatedSaveWeight by animateFloatAsState(
                        targetValue = saveTargetWeight,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "saveWeight"
                    )
                    val animatedCloseWeight by animateFloatAsState(
                        targetValue = closeTargetWeight,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "closeWeight"
                    )

                    // Tactile press scale feedback (squish & bounce)
                    val savePressScale by animateFloatAsState(
                        targetValue = if (isSavePressed && canSave) 0.96f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "savePressScale"
                    )
                    val closePressScale by animateFloatAsState(
                        targetValue = if (isClosePressed) 0.96f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "closePressScale"
                    )

                    // Color transitions
                    val saveBgColor by animateColorAsState(
                        targetValue = if (canSave) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "saveBgColor"
                    )
                    val saveContentColor by animateColorAsState(
                        targetValue = if (canSave) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "saveContentColor"
                    )

                    // M3 Connected Button Group Shapes (24dp outer, 6dp connected inner)
                    val saveShape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp
                    )
                    val closeShape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp
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
