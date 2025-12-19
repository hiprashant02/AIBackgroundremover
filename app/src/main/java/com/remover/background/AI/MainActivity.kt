package com.remover.background.AI

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remover.background.AI.ui.screens.EditorScreen
import com.remover.background.AI.ui.screens.HomeScreen
import com.remover.background.AI.ui.screens.AboutDeveloperScreen
import com.remover.background.AI.ui.screens.PrivacyPolicyScreen
import com.remover.background.AI.ui.theme.AIBackgroundRemoverTheme
import com.remover.background.AI.viewmodel.EditorViewModel
import com.remover.background.AI.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.res.Configuration
import com.remover.background.AI.utils.InAppUpdateManager
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    
    private lateinit var inAppUpdateManager: InAppUpdateManager
    private var isPreferencesLoaded = false
    private var initialTheme = "dark"
    private var initialLanguage = "en"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        val splashScreen = installSplashScreen()
        
        // Keep splash screen visible until preferences are loaded
        splashScreen.setKeepOnScreenCondition { !isPreferencesLoaded }
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize AdMob
        MobileAds.initialize(this) {}

        // Initialize In-App Update Manager
        inAppUpdateManager = InAppUpdateManager(this)
        
        // Load preferences BEFORE setContent (blocking but fast)
        val preferencesManager = PreferencesManager(this)
        runBlocking {
            initialTheme = preferencesManager.themeFlow.first()
            initialLanguage = preferencesManager.languageFlow.first()
        }
        
        // Apply language locale immediately
        applyLocale(initialLanguage)
        
        isPreferencesLoaded = true

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val prefManager = remember { preferencesManager }
            
            // Theme State - initialized with pre-loaded value
            var isDarkTheme by remember { mutableStateOf(initialTheme == "dark") }
            // Language State - initialized with pre-loaded value
            var currentLanguage by remember { mutableStateOf(initialLanguage) }
            val coroutineScope = rememberCoroutineScope()

            AIBackgroundRemoverTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val viewModel: EditorViewModel = viewModel()

                        // Initialize the ViewModel with context
                        viewModel.initialize(this@MainActivity)

                        NavHost(
                            navController = navController,
                            startDestination = "home"
                        ) {
                            composable("home") {
                                HomeScreen(
                                    onImageSelected = { uri ->
                                        viewModel.loadImage(this@MainActivity, uri)
                                        navController.navigate("editor")
                                    },
                                    isDarkTheme = isDarkTheme,
                                    onToggleTheme = { 
                                        isDarkTheme = !isDarkTheme
                                        coroutineScope.launch {
                                            prefManager.setTheme(if (isDarkTheme) "dark" else "light")
                                        }
                                    },
                                    currentLanguage = currentLanguage,
                                    onLanguageSelected = { languageCode ->
                                        currentLanguage = languageCode
                                        coroutineScope.launch {
                                            prefManager.setLanguage(languageCode)
                                            updateLocale(languageCode)
                                        }
                                    },
                                    onAboutClick = {
                                        navController.navigate("about")
                                    }
                                )
                            }

                            composable("editor") {
                                EditorScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        viewModel.reset()
                                        navController.popBackStack()
                                    }
                                )
                            }
                            
                            composable("about") {
                                AboutDeveloperScreen(
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onPrivacyPolicyClick = {
                                        navController.navigate("privacy")
                                    }
                                )
                            }
                            
                            composable("privacy") {
                                PrivacyPolicyScreen(
                                    onBackClick = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }

                        // Snackbar host for update notifications
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }

                // Check for updates and show snackbar
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    checkForAppUpdate(snackbarHostState)
                }
            }
        }
    }
    
    // Apply locale without recreating activity (used on initial load)
    @Suppress("DEPRECATION")
    private fun applyLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
    
    // Update locale and recreate activity (used when user changes language)
    private fun updateLocale(languageCode: String) {
        applyLocale(languageCode)
        // Recreate activity to apply language change
        recreate()
    }
    
    private fun checkForAppUpdate(snackbarHostState: SnackbarHostState) {
        lifecycleScope.launch {
            try {
                val updateInfo = inAppUpdateManager.checkForUpdate()
                
                if (updateInfo != null) {
                    // Update is available
                    // For now, we'll use flexible update (user can continue using the app)
                    // You can change this to immediate update if you want to force the update
                    
                    // Show a snackbar to inform the user
                    lifecycleScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = getString(R.string.update_available),
                            actionLabel = getString(R.string.btn_update),
                            withDismissAction = true
                        )
                        
                        if (result == SnackbarResult.ActionPerformed) {
                            // Start flexible update
                            inAppUpdateManager.startFlexibleUpdate(
                                updateInfo,
                                registerForActivityResult(
                                    androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
                                ) { activityResult ->
                                    if (activityResult.resultCode == RESULT_OK) {
                                        // Update flow completed successfully
                                    }
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Failed to check for updates - this is okay, just log it
                e.printStackTrace()
            }
        }
    }
}
