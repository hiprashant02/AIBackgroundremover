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

class MainActivity : ComponentActivity() {
    
    private lateinit var inAppUpdateManager: InAppUpdateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize AdMob
        MobileAds.initialize(this) {}

        // Initialize In-App Update Manager
        inAppUpdateManager = InAppUpdateManager(this)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val preferencesManager = remember { PreferencesManager(this@MainActivity) }
            
            // Theme State - persisted
            val savedTheme by preferencesManager.themeFlow.collectAsState(initial = "dark")
            var isDarkTheme by remember(savedTheme) { mutableStateOf(savedTheme == "dark") }
            val coroutineScope = rememberCoroutineScope()

            AIBackgroundRemoverTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val viewModel: EditorViewModel = viewModel()
                        val currentLanguage by preferencesManager.languageFlow.collectAsState(
                            initial = "en"
                        )

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
                                            preferencesManager.setTheme(if (isDarkTheme) "dark" else "light")
                                        }
                                    },
                                    currentLanguage = currentLanguage,
                                    onLanguageSelected = { languageCode ->
                                        coroutineScope.launch {
                                            preferencesManager.setLanguage(languageCode)
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
    
    private fun updateLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        
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
