package com.sanelink.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.ui.components.LocalHazeState
import com.sanelink.app.ui.screens.AddEditCustomRuleScreen
import com.sanelink.app.ui.screens.HomeScreen
import com.sanelink.app.ui.screens.RulesScreen
import com.sanelink.app.ui.screens.SettingsScreen
import com.sanelink.app.ui.theme.LocalMotionScheme
import com.sanelink.app.ui.theme.SaneLinkTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QUICK_CLEAN = "com.sanelink.app.EXTRA_QUICK_CLEAN"
    }

    private var isQuickCleaning = false
    private var quickCleanExecuted = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkQuickCleanIntent(intent)
        
        // True edge-to-edge with transparent bars and zero OS contrast scrim
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }

        setContent {
            SaneLinkTheme {
                val scope = rememberCoroutineScope()
                var selectedTab by remember { mutableIntStateOf(0) }
                var rulesSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: Built-in, 1: Custom

                // Full-screen page navigation state for Add/Edit Custom Rule
                var isAddingCustomRule by remember { mutableStateOf(false) }
                var editingCustomRule by remember { mutableStateOf<CustomRule?>(null) }
                val isEditingRule = isAddingCustomRule || editingCustomRule != null

                val motionScheme = LocalMotionScheme.current

                // Material 3 Expressive full-page forward/backward spring transition
                AnimatedContent(
                    targetState = isEditingRule,
                    transitionSpec = {
                        if (targetState) {
                            // Forward navigation into Add/Edit Custom Rule screen (slides in from right)
                            (slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                    stiffness = motionScheme.defaultSpatial.stiffness
                                ),
                                initialOffsetX = { fullWidth -> fullWidth }
                            ) + fadeIn(
                                animationSpec = motionScheme.fastEffects
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                        stiffness = motionScheme.defaultSpatial.stiffness
                                    ),
                                    targetOffsetX = { fullWidth -> -fullWidth / 4 }
                                ) + fadeOut(
                                    animationSpec = motionScheme.fastEffects
                                )
                            )
                        } else {
                            // Backward navigation returning to main screen (slides out to right)
                            (slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                    stiffness = motionScheme.defaultSpatial.stiffness
                                ),
                                initialOffsetX = { fullWidth -> -fullWidth / 4 }
                            ) + fadeIn(
                                animationSpec = motionScheme.fastEffects
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                        stiffness = motionScheme.defaultSpatial.stiffness
                                    ),
                                    targetOffsetX = { fullWidth -> fullWidth }
                                ) + fadeOut(
                                    animationSpec = motionScheme.fastEffects
                                )
                            )
                        }
                    },
                    label = "ruleEditorTransition"
                ) { editing ->
                    if (editing) {
                        BackHandler {
                            isAddingCustomRule = false
                            editingCustomRule = null
                        }

                        AddEditCustomRuleScreen(
                            ruleToEdit = editingCustomRule,
                            onBack = {
                                isAddingCustomRule = false
                                editingCustomRule = null
                            },
                            onSave = { rule ->
                                scope.launch {
                                    SaneLinkApp.instance.repository.saveCustomRule(rule)
                                    rulesSubTab = 1 // Immediately return to the Custom tab to see the saved/updated rule
                                    isAddingCustomRule = false
                                    editingCustomRule = null
                                }
                            }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        AnimatedContent(
                                            targetState = when (selectedTab) {
                                                0 -> "SaneLink"
                                                1 -> "Sanitization Rules"
                                                else -> "Settings"
                                            },
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
                                            label = "topBarTitleTransition"
                                        ) { titleText ->
                                            Text(
                                                text = titleText,
                                                fontWeight = FontWeight.Black,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            }
                        ) { innerPadding ->
                            val hazeState = rememberHazeState()

                            CompositionLocalProvider(LocalHazeState provides hazeState) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Edge-to-edge screen content with spring-driven directional tab transition
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = innerPadding.calculateTopPadding())
                                    ) {
                                        AnimatedContent(
                                            targetState = selectedTab,
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
                                            label = "tabContentTransition"
                                        ) { tab ->
                                            when (tab) {
                                                0 -> HomeScreen()
                                                1 -> RulesScreen(
                                                    selectedSubTab = rulesSubTab,
                                                    onSubTabChanged = { rulesSubTab = it },
                                                    onAddCustomRule = { isAddingCustomRule = true },
                                                    onEditCustomRule = { rule -> editingCustomRule = rule }
                                                )
                                                2 -> SettingsScreen()
                                            }
                                        }
                                    }

                                // Floating Expressive Frosted Blur Navigation Bar
                                val navBarShape = RoundedCornerShape(32.dp)
                                val blurStyle = HazeBlurStyle.Material3()

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 20.dp)
                                        .navigationBarsPadding()
                                        .padding(bottom = 12.dp)
                                        .clip(navBarShape)
                                        .hazeBlur(
                                            input = HazeInput.Sources(hazeState),
                                            style = blurStyle,
                                            performanceMode = HazePerformanceMode.Performance
                                        ),
                                    shape = navBarShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
                                NavigationBar(
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.height(64.dp)
                                ) {
                                    val navItems = listOf(
                                        Triple("Dashboard", R.drawable.ic_cleaning_services, 0),
                                        Triple("Rules", R.drawable.ic_shield, 1),
                                        Triple("Settings", R.drawable.ic_settings, 2)
                                    )

                                    navItems.forEach { (title, iconRes, index) ->
                                        val isSelected = selectedTab == index
                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.15f else 1.0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "navIconScale_$index"
                                        )

                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { selectedTab = index },
                                            icon = {
                                                Icon(
                                                    painter = painterResource(iconRes),
                                                    contentDescription = title,
                                                    modifier = Modifier.graphicsLayer {
                                                        scaleX = iconScale
                                                        scaleY = iconScale
                                                    }
                                                )
                                            },
                                            label = { Text(title, fontWeight = FontWeight.SemiBold) },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkQuickCleanIntent(intent)
    }

    private fun checkQuickCleanIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_CLEAN, false) == true) {
            isQuickCleaning = true
            quickCleanExecuted = false
            if (hasWindowFocus()) {
                quickCleanExecuted = true
                performQuickClean()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isQuickCleaning && !quickCleanExecuted) {
            quickCleanExecuted = true
            performQuickClean()
        }
    }

    private fun performQuickClean() {
        lifecycleScope.launch {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0)?.text?.toString() else null

            if (!text.isNullOrBlank()) {
                val app = SaneLinkApp.instance
                val sanitizer = app.repository.getSanitizer()
                val result = sanitizer.sanitize(text)

                if (result.wasModified && result.cleanText != text) {
                    val newClip = ClipData.newPlainText("sanitized_url", result.cleanText)
                    cm?.setPrimaryClip(newClip)

                    app.preferences.recordSanitization(result.removedParameters.size)

                    if (app.preferences.hapticFeedbackEnabled.first()) {
                        triggerHaptic()
                    }
                    if (app.preferences.showToastEnabled.first()) {
                        val count = result.removedParameters.size
                        val msg = if (count > 0) {
                            "Cleaned! Removed $count tracker${if (count > 1) "s" else ""}"
                        } else {
                            "Clean link copied to clipboard"
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (app.preferences.showToastEnabled.first()) {
                        Toast.makeText(this@MainActivity, "No tracking links found", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (SaneLinkApp.instance.preferences.showToastEnabled.first()) {
                    Toast.makeText(this@MainActivity, "No tracking links found", Toast.LENGTH_SHORT).show()
                }
            }

            moveTaskToBack(true)
            finish()
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35)
            }
        } catch (_: Exception) {
            // Ignore if vibration unavailable
        }
    }
}
