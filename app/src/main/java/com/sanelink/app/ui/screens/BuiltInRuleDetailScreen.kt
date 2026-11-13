package com.sanelink.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.ProviderRule
import com.sanelink.app.core.model.UserPatch
import com.sanelink.app.ui.components.AutoFitTitleText
import com.sanelink.app.ui.components.ExpressiveDualActionButtonBar
import com.sanelink.app.ui.components.ExpressiveSwitch
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuiltInRuleDetailScreen(
    providerId: String,
    initialPatchMode: Boolean = false,
    onClose: () -> Unit,
    onSavePatch: (UserPatch) -> Unit,
    onResetPatch: () -> Unit
) {
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()
    val motionScheme = LocalMotionScheme.current

    val clearUrlsData by app.repository.clearUrlsData.collectAsState()
    val disabledProviderIds by app.repository.disabledProviderIds.collectAsState()
    val bundledPatchedProviderIds by app.repository.bundledPatchedProviderIds.collectAsState()
    val userPatchedProviderIds by app.repository.userPatchedProviderIds.collectAsState()

    val rule = clearUrlsData.providers[providerId] ?: ProviderRule()
    val isProviderActive = !disabledProviderIds.contains(providerId)
    val isBundled = bundledPatchedProviderIds.contains(providerId)
    val isUser = userPatchedProviderIds.contains(providerId)

    var isPatchMode by rememberSaveable { mutableStateOf(initialPatchMode) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Intercept system back press when in patch mode
    BackHandler {
        if (isPatchMode) {
            isPatchMode = false
        } else {
            onClose()
        }
    }

    // Interactive Sample URL State
    val defaultDummy = remember(providerId, rule) { generateDummyUrl(providerId, rule) }
    var testUrlInput by remember(defaultDummy) { mutableStateOf(defaultDummy) }

    val sanitizer = remember { app.repository.getSanitizer() }
    val testResult = remember(testUrlInput, sanitizer) {
        if (testUrlInput.isNotBlank()) sanitizer.sanitizeSingleUrl(testUrlInput) else null
    }

    // Patch Customization State
    val userPatch = remember(providerId, userPatchedProviderIds) { app.repository.getUserPatch(providerId) }
    val baseRules = remember(rule) { (rule.rules + rule.referralMarketing).distinct() }
    val removedRules = remember(userPatch, rule) {
        mutableStateListOf<String>().apply {
            userPatch?.removedRules?.let { addAll(it) }
        }
    }
    val addedRules = remember(userPatch, rule) {
        mutableStateListOf<String>().apply {
            userPatch?.addedRules?.let { addAll(it) }
        }
    }
    var newParamInput by remember { mutableStateOf("") }

    val activePatchParams = remember(baseRules, removedRules.toList(), addedRules.toList()) {
        (baseRules.filterNot { removedRules.contains(it) } + addedRules).distinct()
    }
    val patchSimulatedResult = remember(testUrlInput, activePatchParams) {
        simulateSanitization(testUrlInput, activePatchParams)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val targetTitle = if (isPatchMode) {
                        "Patch ${formatProviderName(providerId)}"
                    } else {
                        formatProviderName(providerId)
                    }
                    AnimatedContent(
                        targetState = targetTitle,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            (slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetY = { -it / 2 }
                            ) + fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )).togetherWith(
                                slideOutVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    targetOffsetY = { it / 2 }
                                ) + fadeOut(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            )
                        },
                        label = "builtInTitleTransition"
                    ) { titleText ->
                        AutoFitTitleText(
                            text = titleText,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            autoScroll = true
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (!isPatchMode) {
                ExpressiveDualActionButtonBar(
                    primaryText = "Patch it",
                    secondaryText = "Close",
                    onPrimaryClick = { isPatchMode = true },
                    onSecondaryClick = onClose
                )
            } else {
                ExpressiveDualActionButtonBar(
                    primaryText = "Save Patch",
                    secondaryText = "Cancel",
                    onPrimaryClick = {
                        val patch = UserPatch(
                            providerId = providerId,
                            isEnabled = true,
                            addedRules = addedRules.toList(),
                            removedRules = removedRules.toList(),
                            customUrlPattern = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSavePatch(patch)
                        isPatchMode = false
                    },
                    onSecondaryClick = {
                        // Discard uncommitted edits
                        removedRules.clear()
                        userPatch?.removedRules?.let { removedRules.addAll(it) }
                        addedRules.clear()
                        userPatch?.addedRules?.let { addedRules.addAll(it) }
                        isPatchMode = false
                    }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = isPatchMode,
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                        stiffness = motionScheme.defaultSpatial.stiffness
                    ),
                    initialOffsetX = { fullWidth -> direction * (fullWidth / 4) }
                ) + fadeIn(animationSpec = motionScheme.fastEffects))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                stiffness = motionScheme.defaultSpatial.stiffness
                            ),
                            targetOffsetX = { fullWidth -> -direction * (fullWidth / 4) }
                        ) + fadeOut(animationSpec = motionScheme.fastEffects)
                    )
            },
            label = "ruleDetailModeTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { patchModeActive ->
            if (!patchModeActive) {
                // ==========================================
                // 1. DETAIL / PREVIEW VIEW
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imeNestedScroll()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Header Status & Switch Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = formatProviderName(providerId),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (isUser) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "User Patched",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    } else if (isBundled) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Patched",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = if (isProviderActive) "Active rule" else "Disabled rule",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            ExpressiveSwitch(
                                checked = isProviderActive,
                                onCheckedChange = { enable ->
                                    scope.launch { app.repository.toggleProvider(providerId, enable) }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Conversion Preview Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "LIVE CONVERSION PREVIEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                IconButton(
                                    onClick = { testUrlInput = defaultDummy },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_refresh),
                                        contentDescription = "Reset Dummy URL",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = testUrlInput,
                                onValueChange = { testUrlInput = it },
                                label = { Text("Sample URL (Editable)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansCode,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (testResult != null && testResult.wasModified) {
                                Text(
                                    text = "CONVERTED RESULT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = testResult.cleanText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansCode,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (testResult.removedParameters.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        testResult.removedParameters.forEach { p ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "-$p",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = GoogleSansCode,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "URL is clean / No parameters removed",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Matched URL Pattern
                    Text(
                        text = "MATCHED URL PATTERN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = rule.urlPattern,
                            fontFamily = GoogleSansCode,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Stripped Tracking Parameters
                    val allRules = rule.rules + rule.referralMarketing
                    if (allRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "TRACKING PARAMETERS STRIPPED (${allRules.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allRules.forEach { param ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = param,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Raw Rules (Path Cleanup)
                    if (rule.rawRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "PATH CLEANUP (RAW RULES)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        rule.rawRules.forEach { raw ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = raw,
                                    fontFamily = GoogleSansCode,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Redirection Unwraps
                    if (rule.redirections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "REDIRECTION UNWRAPPING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        rule.redirections.forEach { redir ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = redir,
                                    fontFamily = GoogleSansCode,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Exceptions
                    if (rule.exceptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "EXCEPTIONS (IGNORED URLS)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        rule.exceptions.forEach { exc ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = exc,
                                    fontFamily = GoogleSansCode,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // If User Patched, option to Reset to Default with confirmation dialog
                    if (isUser) {
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Reset to Default",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                // ==========================================
                // 2. PATCH CUSTOMIZATION VIEW
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imeNestedScroll()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Customize stripped parameters for this site",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Patch Simulation Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "LIVE PATCH PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = testUrlInput,
                                onValueChange = { testUrlInput = it },
                                label = { Text("Sample URL") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansCode,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (patchSimulatedResult.second.isNotEmpty()) {
                                Text(
                                    text = "STRIPPED BY YOUR PATCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = patchSimulatedResult.first,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansCode,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    patchSimulatedResult.second.forEach { p ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "-$p",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = GoogleSansCode,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "URL is clean / No parameters removed",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Add Custom Parameters to Strip
                    Text(
                        text = "ADD CUSTOM PARAMETERS TO STRIP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newParamInput,
                            onValueChange = { newParamInput = it },
                            placeholder = { Text("e.g. tracking_id") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = GoogleSansCode
                            )
                        )

                        ElevatedButton(
                            onClick = {
                                val trimmed = newParamInput.trim()
                                if (trimmed.isNotBlank() && !addedRules.contains(trimmed)) {
                                    addedRules.add(trimmed)
                                    newParamInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = newParamInput.isNotBlank()
                        ) {
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (addedRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            addedRules.forEach { param ->
                                Surface(
                                    onClick = { addedRules.remove(param) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+$param",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = GoogleSansCode,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = "Remove parameter",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Built-in parameters toggle list
                    if (baseRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "BUILT-IN PARAMETERS (TAP TO KEEP)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Parameters marked as (Kept) will NOT be stripped for this site.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            baseRules.forEach { param ->
                                val isKept = removedRules.contains(param)
                                Surface(
                                    onClick = {
                                        if (isKept) {
                                            removedRules.remove(param)
                                        } else {
                                            removedRules.add(param)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isKept) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isKept) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = if (isKept) "$param (Kept)" else param,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GoogleSansCode,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isKept) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Reset button in patch view if already user-patched
                    if (isUser) {
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "Reset to Default",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Confirmation dialog before resetting user patch
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = "Reset Rule to Default?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to discard your custom patch for ${formatProviderName(providerId)}? This will restore the built-in tracking rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        removedRules.clear()
                        addedRules.clear()
                        onResetPatch()
                        isPatchMode = false
                    }
                ) {
                    Text(
                        text = "Reset",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

private fun simulateSanitization(urlStr: String, activeParams: List<String>): Pair<String, List<String>> {
    val uri = try { URI(urlStr) } catch (_: Exception) { return urlStr to emptyList() }
    val rawQuery = uri.rawQuery ?: return urlStr to emptyList()

    val removed = mutableListOf<String>()
    val keptParams = mutableListOf<String>()

    val regexes = activeParams.mapNotNull {
        try { Regex("^$it$", RegexOption.IGNORE_CASE) } catch (_: Exception) { null }
    }

    val pairs = rawQuery.split("&")
    for (pair in pairs) {
        if (pair.isBlank()) continue
        val idx = pair.indexOf("=")
        val key = if (idx > 0) pair.substring(0, idx) else pair

        val matchesRule = regexes.any { regex -> regex.matches(key) } ||
                activeParams.any { it.equals(key, ignoreCase = true) }
        if (matchesRule) {
            removed.add(key)
        } else {
            keptParams.add(pair)
        }
    }

    val newQuery = if (keptParams.isEmpty()) "" else "?" + keptParams.joinToString("&")
    val scheme = uri.scheme ?: "https"
    val host = uri.host ?: ""
    val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
    val path = uri.rawPath ?: ""
    val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""

    val newUrl = "$scheme://$host$port$path$newQuery$fragment"
    return newUrl to removed
}

fun generateDummyUrl(name: String, rule: ProviderRule): String {
    if (rule.redirections.isNotEmpty()) {
        val redir = rule.redirections.first()
        if (redir.contains("google")) {
            return "https://www.google.com/url?q=https%3A%2F%2Fgithub.com%2Ftrending%3Futm_source%3Dgoogle_search&sa=D"
        }
        if (redir.contains("reddit")) {
            return "https://out.reddit.com/url?url=https%3A%2F%2Fwikipedia.org%2Fwiki%2FPrivacy%3Ffbclid%3D123"
        }
        if (redir.contains("youtube")) {
            return "https://www.youtube.com/redirect?q=https%3A%2F%2Fandroid.com%3Futm_campaign%3Dvideo"
        }
    }

    val lowerName = name.lowercase()
    if (lowerName.contains("amazon")) {
        return "https://www.amazon.com/dp/B08N5WRWNW?tag=affiliate-20&ref_=sr_1_1&keywords=wireless+headphones&qid=1600000000"
    }
    if (lowerName == "x" || lowerName.contains("twitter")) {
        return "https://x.com/username/status/1234567890?s=20&t=487abcdef123&ref_src=twsrc"
    }
    if (lowerName.contains("youtube")) {
        return "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=trackingToken123&feature=share&t=42s"
    }
    if (lowerName.contains("reddit")) {
        return "https://www.reddit.com/r/technology/comments/xyz/?utm_source=share&share_id=track123&rdt=456"
    }
    if (lowerName.contains("tiktok")) {
        return "https://www.tiktok.com/@user/video/1234567890?_r=1&_t=8aBCdef&is_from_webapp=1"
    }
    if (lowerName.contains("instagram")) {
        return "https://www.instagram.com/p/Cxyz123/?igshid=YmMyMTA2M2Y%3D&utm_source=ig_web_copy_link"
    }
    if (lowerName.contains("spotify")) {
        return "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=ab12cd34ef56&context=spotify%3Aplaylist"
    }
    if (lowerName.contains("aliexpress")) {
        return "https://www.aliexpress.com/item/100500123.html?spm=a2g0o.productlist.0.0&aff_fcid=123456"
    }
    if (lowerName.contains("ebay")) {
        return "https://www.ebay.com/itm/1234567890?mkevt=1&mkcid=1&campid=533123456"
    }

    val domain = extractDomainFromPattern(rule.urlPattern, name)
    val queryParams = mutableListOf<String>()

    for (r in rule.rules.take(3)) {
        val cleanKey = r.replace(Regex("[^a-zA-Z0-9_]"), "")
        if (cleanKey.isNotBlank()) {
            queryParams.add("$cleanKey=tracking123")
        }
    }
    if (rule.referralMarketing.isNotEmpty()) {
        val refKey = rule.referralMarketing.first().replace(Regex("[^a-zA-Z0-9_]"), "")
        if (refKey.isNotBlank()) queryParams.add("$refKey=affiliate99")
    }
    queryParams.add("utm_source=social_share")

    val queryString = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
    return "https://$domain/item$queryString"
}

private fun extractDomainFromPattern(pattern: String, defaultName: String): String {
    val clean = pattern.replace("\\", "").replace("^", "").replace("https?://", "")
    val match = Regex("([a-zA-Z0-9-]+\\.[a-zA-Z]{2,})").find(clean)
    return match?.value ?: "$defaultName.com"
}

private val formattedNameCache = ConcurrentHashMap<String, String>()

fun formatProviderName(key: String): String {
    return formattedNameCache.getOrPut(key) {
        key.split("_", "-", " ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
