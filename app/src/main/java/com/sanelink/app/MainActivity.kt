package com.sanelink.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.sanelink.app.ui.theme.SaneLinkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

                // If on Add/Edit Custom Rule page: render full page with NO main topbar and NO bottom navbar!
                if (isAddingCustomRule || editingCustomRule != null) {
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
                                    Text(
                                        text = when (selectedTab) {
                                            0 -> "SaneLink"
                                            1 -> "Sanitization Rules"
                                            else -> "Settings"
                                        },
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleLarge
                                    )
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
                                // Edge-to-edge screen content (scrolls underneath the floating nav bar and floating buttons)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = innerPadding.calculateTopPadding())
                                ) {
                                    when (selectedTab) {
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
                                    NavigationBarItem(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_cleaning_services),
                                                contentDescription = "Dashboard"
                                            )
                                        },
                                        label = { Text("Dashboard", fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_shield),
                                                contentDescription = "Rules"
                                            )
                                        },
                                        label = { Text("Rules", fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_settings),
                                                contentDescription = "Settings"
                                            )
                                        },
                                        label = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
