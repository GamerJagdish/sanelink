package com.sanelink.app.ui.screens

import com.sanelink.app.ui.components.RuleTag
import com.sanelink.app.ui.components.PatchedBadge
import com.sanelink.app.ui.theme.rememberReducedMotion
import com.sanelink.app.ui.theme.tabSwitchTransition
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.ProviderRule
import com.sanelink.app.core.model.UserPatch
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
    onSelectBuiltInRule: (String) -> Unit = {},
    builtInListState: LazyListState = rememberLazyListState(),
    customListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()

    // Deleting a custom rule can't be undone, so ask first
    var ruleToDelete by remember { mutableStateOf<CustomRule?>(null) }
    ruleToDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete \"${pending.name}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("This custom rule will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { app.repository.deleteCustomRule(pending.id) }
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
    val hazeState = LocalHazeState.current

    val clearUrlsData by app.repository.clearUrlsData.collectAsState()
    val customRules by app.repository.customRules.collectAsState()
    val disabledProviderIds by app.repository.disabledProviderIds.collectAsState()
    val bundledPatchedProviderIds by app.repository.bundledPatchedProviderIds.collectAsState()
    val userPatchedProviderIds by app.repository.userPatchedProviderIds.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOnlyPatched by rememberSaveable { mutableStateOf(false) }

    val motionScheme = LocalMotionScheme.current
    val itemPlacementSpec = remember(motionScheme) {
        motionScheme.defaultSpatialSpec<IntOffset>()
    }
    val itemFadeSpec = motionScheme.fastEffects

    val providersList = remember(clearUrlsData, searchQuery, showOnlyPatched, bundledPatchedProviderIds, userPatchedProviderIds) {
        val all = clearUrlsData.providers.toList()
        var filtered = all
        if (showOnlyPatched) {
            filtered = filtered.filter { (name, _) ->
                bundledPatchedProviderIds.contains(name) || userPatchedProviderIds.contains(name)
            }
        }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { (name, rule) ->
                name.contains(searchQuery, ignoreCase = true) ||
                        rule.urlPattern.contains(searchQuery, ignoreCase = true)
            }
        }
        // Alphabetical, so a provider is easy to find by scrolling
        filtered.sortedBy { (name, _) -> name.lowercase() }
    }

    val filteredCustomRules = remember(customRules, searchQuery) {
        if (searchQuery.isBlank()) {
            customRules
        } else {
            customRules.filter { rule ->
                rule.name.contains(searchQuery, ignoreCase = true) ||
                        rule.urlPattern.contains(searchQuery, ignoreCase = true) ||
                        (rule.matchDomain?.contains(searchQuery, ignoreCase = true) == true) ||
                        (rule.swapToDomain?.contains(searchQuery, ignoreCase = true) == true) ||
                        rule.stripParameters.any { it.contains(searchQuery, ignoreCase = true) } ||
                        (rule.rewritePattern?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    // Counts follow the active search and "patched only" filter
    val builtInCount = providersList.size
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
                val reducedMotion = rememberReducedMotion()
                AnimatedContent(
                    targetState = selectedSubTab,
                    transitionSpec = { motionScheme.tabSwitchTransition(if (targetState > initialState) 1 else -1, reducedMotion) },
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Ignore disabled ids of providers that no longer exist
                                    val activeCount = clearUrlsData.providers.keys.count { it !in disabledProviderIds }
                                    Text(
                                        text = when {
                                            showOnlyPatched -> "${providersList.size} patched"
                                            searchQuery.isNotBlank() -> "${providersList.size} matching"
                                            else -> "$activeCount active"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val patchedCount = (bundledPatchedProviderIds + userPatchedProviderIds).size
                                    Surface(
                                        onClick = { showOnlyPatched = !showOnlyPatched },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (showOnlyPatched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(
                                            1.dp,
                                            if (showOnlyPatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Text(
                                            text = "Patched ($patchedCount)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (showOnlyPatched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Material 3 Expressive Bulk Rules Toggle Button
                                val toggleInteraction = remember { MutableInteractionSource() }
                                val isTogglePressed by toggleInteraction.collectIsPressedAsState()

                                val toggleScaleX by animateFloatAsState(
                                    targetValue = if (isTogglePressed) 0.98f else 1f,
                                    animationSpec = motionScheme.fastSpatial,
                                    label = "bulkToggleScaleX"
                                )
                                val toggleScaleY by animateFloatAsState(
                                    targetValue = if (isTogglePressed) 0.92f else 1f,
                                    animationSpec = motionScheme.fastSpatial,
                                    label = "bulkToggleScaleY"
                                )
                                val toggleCornerRadius by animateDpAsState(
                                    targetValue = if (isTogglePressed) 8.dp else 12.dp,
                                    animationSpec = motionScheme.fastSpatialSpec(),
                                    label = "bulkToggleCornerRadius"
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
                                    shape = RoundedCornerShape(toggleCornerRadius),
                                    color = if (allBuiltInDisabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = toggleScaleX
                                        scaleY = toggleScaleY
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
                                    state = builtInListState,
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
                                        val isBundled = bundledPatchedProviderIds.contains(name)
                                        val isUser = userPatchedProviderIds.contains(name)

                                        BuiltInProviderCard(
                                            name = name,
                                            rule = rule,
                                            isActive = isProviderActive,
                                            isBundledPatched = isBundled,
                                            isUserPatched = isUser,
                                            onToggle = { enable ->
                                                scope.launch { app.repository.toggleProvider(name, enable) }
                                            },
                                            onClick = {
                                                onSelectBuiltInRule(name)
                                            },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = itemFadeSpec,
                                                placementSpec = itemPlacementSpec,
                                                fadeOutSpec = itemFadeSpec
                                            )
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
                                state = customListState,
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
                                        onDelete = { ruleToDelete = rule },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = itemFadeSpec,
                                            placementSpec = itemPlacementSpec,
                                            fadeOutSpec = itemFadeSpec
                                        )
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
}

/**
 * Card shell shared by built-in and custom rule cards: press squash and corner morph, and
 * muted colours when the rule is switched off. [content] receives the title colour.
 */
@Composable
private fun RuleCardSurface(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (titleColor: Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "ruleCardPressScale"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 16.dp else 22.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "ruleCardCornerRadius"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = motionScheme.defaultColorEffects,
        label = "ruleCardContainerColor"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        animationSpec = motionScheme.fastColorEffects,
        label = "ruleCardTitleColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isActive) 0.35f else 0.2f)),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        content(titleColor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuiltInProviderCard(
    name: String,
    rule: ProviderRule,
    isActive: Boolean,
    isBundledPatched: Boolean,
    isUserPatched: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RuleCardSurface(isActive = isActive, onClick = onClick, modifier = modifier) { titleColor ->
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
                    PatchedBadge(isUserPatched = isUserPatched, isBundledPatched = isBundledPatched)

                    // Muted when the provider is switched off
                    val idleContainer = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    val idleContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    val ruleCount = rule.rules.size + rule.referralMarketing.size
                    if (ruleCount > 0) {
                        RuleTag(
                            text = "$ruleCount params",
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else idleContainer,
                            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else idleContent
                        )
                    }
                    if (rule.redirections.isNotEmpty()) {
                        RuleTag(
                            text = "Unwraps Redirect",
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else idleContainer,
                            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else idleContent
                        )
                    }
                    if (rule.rawRules.isNotEmpty()) {
                        RuleTag(
                            text = "Cleans Path",
                            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) else idleContainer,
                            contentColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else idleContent
                        )
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
    RuleCardSurface(isActive = rule.isEnabled, onClick = onEdit, modifier = modifier) { titleColor ->
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
                        // Simple-mode rules show their plain website instead of the generated regex
                        text = rule.matchDomain ?: rule.urlPattern,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = GoogleSansCode,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    )

                    // Frontend Rewrite preview
                    val rewriteLabel = when {
                        !rule.swapToDomain.isNullOrBlank() -> "Opens on: ${rule.swapToDomain}"
                        !rule.rewritePattern.isNullOrBlank() && !rule.rewriteReplacement.isNullOrBlank() ->
                            "Rewrites to: ${rule.rewriteReplacement}"
                        else -> null
                    }
                    if (rewriteLabel != null) {
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
                                text = rewriteLabel,
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
