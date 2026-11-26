package com.sanelink.app.ui.screens

import com.sanelink.app.ui.components.SectionLabel
import com.sanelink.app.core.sanitizer.CustomRuleValidator
import com.sanelink.app.ui.components.StringStateListSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.sanitizer.DomainSwapper
import com.sanelink.app.ui.components.AutoFitTitleText
import com.sanelink.app.ui.components.DomainSwapEditor
import com.sanelink.app.ui.components.ExpressiveConnectedButtonGroup
import com.sanelink.app.ui.components.ExpressiveDualActionButtonBar
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.components.ExpressiveSwitch
import com.sanelink.app.ui.components.ExpressiveTactileButton
import com.sanelink.app.ui.components.SuggestionChip
import com.sanelink.app.ui.components.parseDomainSwapInput
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme

/** One-tap starting points. Plain domains only; no regex. */
private data class RulePreset(
    val label: String,
    val name: String,
    val website: String,
    val swapTo: String,
    val params: List<String>
)

private val rulePresets = listOf(
    RulePreset("Reddit → Redlib", "Reddit to Redlib", "reddit.com", "redlib.catsarch.com", listOf("share_id", "rdt", "correlation_id")),
    RulePreset("X → FixupX", "X to FixupX", "x.com", "fixupx.com", listOf("s", "t")),
    RulePreset("YouTube → Invidious", "YouTube to Invidious", "youtube.com", "yewtu.be", listOf("si", "feature", "pp")),
    RulePreset("Instagram → InstaFix", "Instagram to InstaFix", "instagram.com", "ddinstagram.com", emptyList()),
)

private const val MODE_SIMPLE = 0
private const val MODE_ADVANCED = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCustomRuleScreen(
    ruleToEdit: CustomRule?,
    onBack: () -> Unit,
    onSave: (CustomRule) -> Unit,
    onPatchBuiltIn: (providerId: String, prefill: PatchPrefill) -> Unit = { _, _ -> }
) {
    val motionScheme = LocalMotionScheme.current
    // New rules and rules made in simple mode open in simple mode; regex rules open in advanced.
    var mode by rememberSaveable(ruleToEdit?.id) {
        mutableIntStateOf(if (ruleToEdit == null || ruleToEdit.matchDomain != null) MODE_SIMPLE else MODE_ADVANCED)
    }
    var name by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.name ?: "") }
    var website by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.matchDomain ?: "") }
    var swapTo by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.swapToDomain ?: "") }
    val params = rememberSaveable(ruleToEdit?.id, saver = StringStateListSaver) {
        mutableStateListOf<String>().apply { ruleToEdit?.stripParameters?.let(::addAll) }
    }
    // New rules keep SaneLink's built-in cleaning by default; existing rules keep their setting.
    var keepBuiltIn by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.keepBuiltInCleaning ?: true) }

    // Advanced (regex) fields
    var urlPattern by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.urlPattern ?: "") }
    var rewritePattern by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.rewritePattern ?: "") }
    var rewriteReplacement by rememberSaveable(ruleToEdit?.id) { mutableStateOf(ruleToEdit?.rewriteReplacement ?: "") }

    val domain = DomainSwapper.normalizeDomain(website)
    val swapInput = parseDomainSwapInput(website, swapTo)

    val clearUrlsData by SaneLinkApp.instance.repository.clearUrlsData.collectAsState()
    var dismissedConflictProvider by rememberSaveable { mutableStateOf<String?>(null) }
    val matchingProvider = remember(mode, name, domain, urlPattern, clearUrlsData, dismissedConflictProvider) {
        findMatchingProvider(
            clearUrlsData = clearUrlsData,
            name = name,
            probeUrls = if (mode == MODE_SIMPLE) {
                domain?.let { listOf("https://$it/", "https://www.$it/") }.orEmpty()
            } else {
                emptyList()
            },
            regexText = if (mode == MODE_ADVANCED) urlPattern else ""
        )?.takeIf { it != dismissedConflictProvider }
    }

    val isEditing = ruleToEdit != null
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Validation: broken regexes and empty rules can't be saved
    val urlPatternError = CustomRuleValidator.regexError(urlPattern)
    val rewriteError = CustomRuleValidator.rewriteError(rewritePattern, rewriteReplacement)
    val isNoOpSimpleRule = CustomRuleValidator.isNoOpSimpleRule(keepBuiltIn, params.size, swapInput.isValid)
    val canSave = when (mode) {
        MODE_SIMPLE -> domain != null && (swapInput.isEmpty || swapInput.isValid) && !isNoOpSimpleRule
        else -> name.isNotBlank() && urlPattern.isNotBlank() && urlPatternError == null && rewriteError == null
    }

    val onSaveAction: () -> Unit = save@{
        if (!canSave) return@save
        val rule = if (mode == MODE_SIMPLE && domain != null) {
            val target = swapInput.to.takeIf { swapInput.isValid }
            CustomRule(
                id = ruleToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim().ifBlank { if (target != null) "$domain → $target" else domain },
                urlPattern = DomainSwapper.urlPatternFor(domain),
                stripParameters = params.toList(),
                matchDomain = domain,
                swapToDomain = target,
                keepBuiltInCleaning = keepBuiltIn,
                isEnabled = ruleToEdit?.isEnabled ?: true
            )
        } else {
            CustomRule(
                id = ruleToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                urlPattern = urlPattern.trim(),
                stripParameters = params.toList(),
                rewritePattern = rewritePattern.trim().ifBlank { null },
                rewriteReplacement = rewriteReplacement.trim().ifBlank { null },
                keepBuiltInCleaning = keepBuiltIn,
                isEnabled = ruleToEdit?.isEnabled ?: true
            )
        }
        onSave(rule)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AutoFitTitleText(
                        text = if (isEditing) "Edit Custom Rule" else "Add Custom Rule",
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
            // Presets: one compact, horizontally scrolling row; only when creating a rule
            if (!isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Try",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    rulePresets.forEach { preset ->
                        SuggestionChip(
                            text = preset.label,
                            onClick = {
                                mode = MODE_SIMPLE
                                name = preset.name
                                website = preset.website
                                swapTo = preset.swapTo
                                params.clear()
                                params.addAll(preset.params)
                                keepBuiltIn = true
                                dismissedConflictProvider = null
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            ExpressiveConnectedButtonGroup(
                options = listOf("Simple", "Advanced (regex)"),
                selectedIndex = mode,
                onSelect = { selected ->
                    if (selected == MODE_ADVANCED && urlPattern.isBlank() && domain != null) {
                        urlPattern = DomainSwapper.urlPatternFor(domain)
                    }
                    if (selected == MODE_ADVANCED && name.isBlank() && domain != null) {
                        name = domain
                    }
                    mode = selected
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Smart built-in duplicate suggestion banner
            AnimatedVisibility(
                visible = matchingProvider != null,
                enter = fadeIn(motionScheme.fastEffects) + expandVertically(
                    motionScheme.defaultSpatialSpec()
                ),
                exit = fadeOut(motionScheme.fastEffects) + shrinkVertically(
                    motionScheme.defaultSpatialSpec()
                )
            ) {
                matchingProvider?.let { matchedId ->
                    BuiltInMatchBanner(
                        providerName = formatProviderName(matchedId),
                        onPatch = {
                            // Carry what the user typed into the patch editor
                            onPatchBuiltIn(
                                matchedId,
                                PatchPrefill(
                                    params = params.toList(),
                                    swapFrom = domain.takeIf { mode == MODE_SIMPLE },
                                    swapTo = swapInput.to.takeIf { mode == MODE_SIMPLE && swapInput.isValid }
                                )
                            )
                        },
                        onKeep = { dismissedConflictProvider = matchedId },
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(motionScheme.fastEffects).togetherWith(fadeOut(motionScheme.fastEffects)) },
                label = "customRuleMode"
            ) { currentMode ->
                Column {
                    if (currentMode == MODE_SIMPLE) {
                        SectionLabel("WEBSITE")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it.trim() },
                            label = { Text("Website") },
                            placeholder = { Text("reddit.com") },
                            isError = website.isNotBlank() && domain == null,
                            supportingText = {
                                Text(
                                    if (website.isNotBlank() && domain == null) {
                                        "Enter a website like reddit.com"
                                    } else {
                                        "Also covers www. and other subdomains"
                                    }
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = GoogleSansCode,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                                autoCorrectEnabled = false
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                        )
                    } else {
                        SectionLabel("MATCHING (REGEX)")
                        Spacer(modifier = Modifier.height(8.dp))
                        CodeField(
                            value = urlPattern,
                            onValueChange = { urlPattern = it },
                            label = "URL pattern (regex)",
                            placeholder = "^https?://(?:www\\.)?reddit\\.com",
                            error = urlPatternError
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    SectionLabel("REMOVE THESE PARAMETERS")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The part after ? in a link, e.g. share_id in reddit.com/r/x?share_id=abc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ParameterChipInput(params = params)

                    Spacer(modifier = Modifier.height(18.dp))
                    if (currentMode == MODE_SIMPLE) {
                        SectionLabel("OPEN ON A DIFFERENT SITE (OPTIONAL)")
                        Spacer(modifier = Modifier.height(8.dp))
                        DomainSwapEditor(
                            fromText = website,
                            onFromChange = { website = it },
                            toText = swapTo,
                            onToChange = { swapTo = it },
                            fromEditable = false
                        )
                    } else {
                        SectionLabel("URL REWRITE (OPTIONAL)")
                        Spacer(modifier = Modifier.height(8.dp))
                        CodeField(
                            value = rewritePattern,
                            onValueChange = { rewritePattern = it },
                            label = "Match regex",
                            placeholder = "^https?://(?:www\\.)?reddit\\.com/(.*)",
                            error = if (rewritePattern.isNotBlank()) rewriteError else null
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CodeField(
                            value = rewriteReplacement,
                            onValueChange = { rewriteReplacement = it },
                            label = "Replace with",
                            placeholder = "https://redlib.catsarch.com/$1",
                            error = if (rewritePattern.isBlank()) rewriteError else null,
                            imeAction = ImeAction.Done,
                            onDone = { focusManager.clearFocus() }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    ExpressiveSegmentedListItem(
                        headline = {
                            Text(
                                text = "Keep built-in cleaning",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supporting = {
                            Text(
                                text = if (keepBuiltIn) {
                                    "SaneLink's own tracker list and patches still apply to this site, on top of this rule."
                                } else {
                                    "Only this rule applies to matching links; built-in tracker removal is skipped."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            ExpressiveSwitch(checked = keepBuiltIn, onCheckedChange = { keepBuiltIn = it })
                        },
                        onClick = { keepBuiltIn = !keepBuiltIn }
                    )

                    AnimatedVisibility(visible = currentMode == MODE_SIMPLE && domain != null && isNoOpSimpleRule) {
                        Text(
                            text = "This rule doesn't change anything yet. Add a parameter to remove or a site to open on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (currentMode == MODE_SIMPLE) "Rule name (optional)" else "Rule name") },
                        placeholder = {
                            Text(
                                when {
                                    currentMode == MODE_ADVANCED -> "e.g. Reddit to Redlib"
                                    domain != null && swapInput.isValid -> "$domain → ${swapInput.to}"
                                    domain != null -> domain
                                    else -> "e.g. Reddit to Redlib"
                                }
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) keyboardController?.show() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

/**
 * Finds a built-in provider that already covers what the user is creating: by name, by
 * testing the provider's pattern against probe URLs (simple mode), or by text overlap with the
 * user's regex (advanced mode). Catch-all providers are never suggested.
 */
private fun findMatchingProvider(
    clearUrlsData: ClearUrlsData,
    name: String,
    probeUrls: List<String>,
    regexText: String
): String? {
    val cleanName = name.trim().lowercase()
    val cleanRegex = regexText.trim().lowercase()
    if (cleanName.isBlank() && probeUrls.isEmpty() && cleanRegex.isBlank()) return null

    return clearUrlsData.providers.entries.firstOrNull { (providerId, providerRule) ->
        if (providerId.equals("globalRules", ignoreCase = true) || providerRule.urlPattern == ".*") {
            return@firstOrNull false
        }
        val pId = providerId.lowercase()
        val providerRegex = try {
            if (providerRule.urlPattern.isNotBlank()) Regex(providerRule.urlPattern, RegexOption.IGNORE_CASE) else null
        } catch (_: Exception) {
            null
        }
        (cleanName.isNotBlank() && (cleanName == pId || (pId.length >= 3 && cleanName.contains(pId)))) ||
            (providerRegex != null && probeUrls.any { providerRegex.containsMatchIn(it) }) ||
            (cleanRegex.isNotBlank() && (
                cleanRegex.contains(pId) ||
                    (providerRegex != null && providerRegex.containsMatchIn(regexText))
                ))
    }?.key
}

@Composable
private fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = GoogleSansCode,
            color = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) keyboardController?.show() }
    )
}

/** Type a parameter name (or paste several separated by commas/spaces) and tap Add. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParameterChipInput(params: MutableList<String>) {
    var input by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commit() {
        input.split(',', ' ', '&', '\n')
            .map { it.substringBefore('=').trim().trimStart('?') }
            .filter { it.isNotEmpty() && params.none { p -> p.equals(it, ignoreCase = true) } }
            .forEach { params.add(it) }
        input = ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("e.g. share_id") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = GoogleSansCode,
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) keyboardController?.show() }
        )
        ExpressiveTactileButton(
            onClick = { commit() },
            enabled = input.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text("Add", fontWeight = FontWeight.Bold)
        }
    }

    AnimatedVisibility(visible = params.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            params.toList().forEach { param ->
                Surface(
                    onClick = { params.remove(param) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = param,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = GoogleSansCode,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Remove $param",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
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
