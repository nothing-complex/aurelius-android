package com.greyloop.aurelius.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.greyloop.aurelius.data.security.SecureStorage
import com.greyloop.aurelius.ui.chat.ChatScreen
import com.greyloop.aurelius.ui.home.HomeScreen
import com.greyloop.aurelius.ui.settings.SettingsScreen
import com.greyloop.aurelius.ui.theme.AureliusTheme
import com.greyloop.aurelius.ui.theme.darkThemeFromMode
import org.koin.compose.koinInject

sealed class Screen(val route: String, val title: String) {
    data object Home : Screen("home", "Home")
    data object Settings : Screen("settings", "Settings")
}

@Composable
fun AureliusApp(
    secureStorage: SecureStorage = koinInject()
) {
    val themeMode = secureStorage.themeMode
    val darkThemeOverride = darkThemeFromMode(themeMode)
    val isDark = darkThemeOverride ?: androidx.compose.foundation.isSystemInDarkTheme()

    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Settings)

    AureliusTheme(darkTheme = isDark) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, contentDescription = null)
                                    Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onChatClick = { chatId ->
                            navController.navigate("chat/$chatId")
                        },
                        onNewChat = {
                            navController.navigate("chat/new")
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("chat/{chatId}") { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId")
                    ChatScreen(
                        chatId = if (chatId == "new") null else chatId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
