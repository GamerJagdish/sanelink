package com.sanelink.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.ProviderRule
import com.sanelink.app.ui.components.ExpressiveConnectedButtonGroup
import com.sanelink.app.ui.components.ExpressiveConnectedButtonItem
import com.sanelink.app.ui.components.ExpressiveConnectedIconButtonGroup
import com.sanelink.app.ui.components.ExpressiveFloatingButton
import com.sanelink.app.ui.components.ExpressiveSearchBar
import com.sanelink.app.ui.components.ExpressiveSwitch
import com.sanelink.app.ui.components.LocalHazeState
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    selectedSubTab: Int = 0,
    onSubTabChanged: (Int) -> Unit = {},
    onAddCustomRule: () -> Unit = {},
    onEditCustomRule: (CustomRule) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current

    val clearUrlsData by app.repository.clearUrlsData.collectAsState()
    val customRules by app.repository.customRules.collectAsState()
    val disabledProviderIds by app.repository.disabledProviderIds.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    // Dialog / Bottom Sheet state for previewing built-in rule
    var previewProvider by remember { mutableStateOf<Pair<String, ProviderRule>?>(null) }

    val providersList = remember(clearUrlsData, searchQuery) {
        val all = clearUrlsData.providers.toList()
        if (searchQuery.isBlank()) {
            all
        } else {
            all.filter { (name, rule) ->
                name.contains(searchQuery, ignoreCase = true) ||
                        rule.urlPattern.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredCustomRules = remember(customRules, searchQuery) {
        if (searchQuery.isBlank()) {
            customRules
        } else {
            customRules.filter { rule ->
                rule.name.contains(searchQuery, ignoreCase = true) ||
                        rule.urlPattern.contains(searchQuery, ignoreCase = true) ||
                        rule.stripParameters.any { it.contains(searchQuery, ignoreCase = true) } ||
                        (rule.rewritePattern?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val builtInCount = if (searchQuery.isBlank()) clearUrlsData.providers.size else providersList.size
    val customCount = if (searchQuery.isBlank()) customRules.size else filteredCustomRules.size

    val allBuiltInDisabled = remember(clearUrlsData, disabledProviderIds) {
        clearUrlsData.providers.isNotEmpty() && disabledProviderIds.containsAll(clearUrlsData.providers.keys)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        // Material 3 Expressive Search Bar (above the tabs for global filtering)
        ExpressiveSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search rules...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Material 3 Expressive Connected Button Group: Built-in vs Custom
        ExpressiveConnectedButtonGroup(
            items = listOf(
                ExpressiveConnectedButtonItem(
                    label = "Built-in",
                    count = builtInCount
                ),
                ExpressiveConnectedButtonItem(
                    label = "Custom",
                    count = customCount
                )
            ),
            selectedIndex = selectedSubTab,
            onSelect = onSubTabChanged,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            ) {
                val motionScheme = LocalMotionScheme.current
                AnimatedContent(
                    targetState = selectedSubTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            initialOffsetX = { fullWidth -> direction * (fullWidth / 4) }
                        ) + fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                targetOffsetX = { fullWidth -> -direction * (fullWidth / 4) }
                            ) + fadeOut(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        )
                    },
                    label = "rulesSubTabTransition"
                ) { subTab ->
                    if (subTab == 0) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Action bar: Provider count and simplified "On/Off" button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val activeCount = clearUrlsData.providers.size - disabledProviderIds.size
                                Text(
                                    text = if (searchQuery.isBlank()) "$activeCount active rules" else "${providersList.size} matching rules",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Material 3 Expressive Bulk Rules Toggle Button
                                val toggleInteraction = remember { MutableInteractionSource() }
                                val isTogglePressed by toggleInteraction.collectIsPressedAsState()

                                val toggleScale by animateFloatAsState(
                                    targetValue = if (isTogglePressed) 0.96f else 1f,
                                    animationSpec = motionScheme.fastSpatial,
                                    label = "bulkToggleScale"
                                )

                                Surface(
                                    onClick = {
                                        scope.launch {
                                            if (allBuiltInDisabled) {
                                                app.repository.enableAllProviders()
                                            } else {
                                                app.repository.disableAllProviders()
                                            }
                                        }
                                    },
                                    interactionSource = toggleInteraction,
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (allBuiltInDisabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = toggleScale
                                        scaleY = toggleScale
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(if (allBuiltInDisabled) R.drawable.ic_check else R.drawable.ic_close),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (allBuiltInDisabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (allBuiltInDisabled) "Enable All" else "Disable All",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (allBuiltInDisabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (providersList.isEmpty() && searchQuery.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 64.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_search),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "No Matching Built-in Rules",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "No built-in providers match \"$searchQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 220.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = providersList,
                                        key = { it.first },
                                        contentType = { "built_in_provider" }
                                    ) { (name, rule) ->
                                        val isProviderActive = !disabledProviderIds.contains(name)

                                        BuiltInProviderCard(
                                            name = name,
                                            rule = rule,
                                            isActive = isProviderActive,
                                            onToggle = { enable ->
                                                scope.launch { app.repository.toggleProvider(name, enable) }
                                            },
                                            onClick = {
                                                previewProvider = name to rule
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Custom Rules Tab
                        if (customRules.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_auto_fix_high),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "No Custom Rules Yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Tap the button below to add your own custom rule to strip tracking parameters or rewrite frontends.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        } else if (filteredCustomRules.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_search),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "No Matching Custom Rules",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "No custom rules match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 220.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredCustomRules,
                                    key = { it.id },
                                    contentType = { "custom_rule" }
                                ) { rule ->
                                    CustomRuleCard(
                                        rule = rule,
                                        onToggle = { enable ->
                                            scope.launch { app.repository.toggleCustomRule(rule.id, enable) }
                                        },
                                        onEdit = { onEditCustomRule(rule) },
                                        onDelete = {
                                            scope.launch { app.repository.deleteCustomRule(rule.id) }
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Material 3 Expressive Floating "Add Custom Rule" pill button with spring physics and frosted blur
            ExpressiveFloatingButton(
                text = "Add Custom Rule",
                onClick = onAddCustomRule,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
                    .height(52.dp)
            )
        }
    }

    // Rule Details Preview Modal Bottom Sheet with Interactive Dummy URL Generator
    previewProvider?.let { (name, rule) ->
        val isProviderActive = !disabledProviderIds.contains(name)
        RulePreviewBottomSheet(
            name = name,
            rule = rule,
            isActive = isProviderActive,
            onToggle = { enable ->
                scope.launch { app.repository.toggleProvider(name, enable) }
            },
            onDismiss = { previewProvider = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuiltInProviderCard(
    name: String,
    rule: ProviderRule,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "providerPressScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "providerCornerRadius"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motionScheme.defaultColorEffects,
        label = "providerContainerColor"
    )

    val titleColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        animationSpec = motionScheme.fastColorEffects,
        label = "providerTitleColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isActive) 0.35f else 0.2f)
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatProviderName(name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val ruleCount = rule.rules.size + rule.referralMarketing.size
                    if (ruleCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$ruleCount params",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }

                    if (rule.redirections.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Unwraps Redirect",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }

                    if (rule.rawRules.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Cleans Path",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveSwitch(
                    checked = isActive,
                    onCheckedChange = onToggle
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRuleCard(
    rule: CustomRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "customRulePressScale"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "customRuleCornerRadius"
    )

    val containerColor by animateColorAsState(
        targetValue = if (rule.isEnabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = motionScheme.defaultColorEffects,
        label = "customRuleContainerColor"
    )

    val titleColor by animateColorAsState(
        targetValue = if (rule.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        animationSpec = motionScheme.fastColorEffects,
        label = "customRuleTitleColor"
    )

    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (rule.isEnabled) 0.35f else 0.2f)
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left Content: Title, Pattern, Rewrite preview, Parameter chips
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        text = rule.urlPattern,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = GoogleSansCode,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    )

                    // Frontend Rewrite preview
                    if (!rule.rewritePattern.isNullOrBlank() && !rule.rewriteReplacement.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (rule.isEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_swap_horiz),
                                contentDescription = null,
                                tint = if (rule.isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Rewrites to: ${rule.rewriteReplacement}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GoogleSansCode,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (rule.isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                }
                            )
                        }
                    }

                    // Parameter badges
                    if (rule.stripParameters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rule.stripParameters.forEach { param ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (rule.isEnabled) {
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = param,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = if (rule.isEnabled) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Content: Toggle on top, Connected Edit & Delete buttons underneath
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveSwitch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggle
                    )

                    // M3 Expressive Connected Icon Button Group: Edit | Delete
                    ExpressiveConnectedIconButtonGroup(
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RulePreviewBottomSheet(
    name: String,
    rule: ProviderRule,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val app = SaneLinkApp.instance
    val motion = LocalMotionScheme.current

    // Interactive Dummy URL state
    val defaultDummy = remember(name, rule) { generateDummyUrl(name, rule) }
    var testUrlInput by remember(name, rule) { mutableStateOf(defaultDummy) }

    val sanitizer = remember { app.repository.getSanitizer() }
    val testResult = remember(testUrlInput, sanitizer) {
        if (testUrlInput.isNotBlank()) sanitizer.sanitizeSingleUrl(testUrlInput) else null
    }

    val resetInteractionSource = remember { MutableInteractionSource() }
    val isResetPressed by resetInteractionSource.collectIsPressedAsState()
    val resetScale by animateFloatAsState(
        targetValue = if (isResetPressed) 0.82f else 1f,
        animationSpec = motion.fastSpatial,
        label = "ResetDummyScale"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatProviderName(name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Rule Details & Live Simulation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ExpressiveSwitch(
                    checked = isActive,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Interactive Dummy URL Live Tester
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
                            interactionSource = resetInteractionSource,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = resetScale
                                    scaleY = resetScale
                                }
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

            // URL Pattern
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

            Spacer(modifier = Modifier.height(24.dp))

            ExpressiveFloatingButton(
                text = "Close Preview",
                onClick = onDismiss,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = null,
                hazeState = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun generateDummyUrl(name: String, rule: ProviderRule): String {
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

private val formattedNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private fun formatProviderName(key: String): String {
    return formattedNameCache.getOrPut(key) {
        key.split("_", "-", " ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
