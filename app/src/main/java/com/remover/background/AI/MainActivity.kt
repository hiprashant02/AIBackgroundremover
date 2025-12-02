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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.remover.background.AI.ui.screens.EditorScreen
import com.remover.background.AI.ui.screens.HomeScreen
import com.remover.background.AI.ui.theme.AIBackgroundRemoverTheme
import com.remover.background.AI.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Theme State
            var isDarkTheme by remember { mutableStateOf(true) } // Default to Dark

            AIBackgroundRemoverTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: EditorViewModel = viewModel()

                    // Initialize the ViewModel with context
                    viewModel.initialize(this)

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
                                onToggleTheme = { isDarkTheme = !isDarkTheme }
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
                    }
                }
            }
        }
    }
}

