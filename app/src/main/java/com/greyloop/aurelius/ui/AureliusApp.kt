package com.greyloop.aurelius.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.greyloop.aurelius.data.security.SecureStorage
import com.greyloop.aurelius.ui.chat.ChatScreen
import com.greyloop.aurelius.ui.home.HomeScreen
import com.greyloop.aurelius.ui.settings.SettingsScreen
import com.greyloop.aurelius.ui.theme.AureliusTheme
import com.greyloop.aurelius.ui.theme.darkThemeFromMode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val HOME_PAGE = 0
private const val SETTINGS_PAGE = 1
private const val PAGE_COUNT = 2

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHAT = "chat"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AureliusApp(
    secureStorage: SecureStorage = koinInject()
) {
    val themeMode = secureStorage.themeMode
    val darkThemeOverride = darkThemeFromMode(themeMode)
    val isDark = darkThemeOverride ?: androidx.compose.foundation.isSystemInDarkTheme()

    val navController = rememberNavController()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    // Check current route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnChatScreen = currentRoute?.startsWith("chat") == true

    AureliusTheme(darkTheme = isDark) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    // NavHost with full navigation graph - always present
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        // Home screen (with pager for Home/Settings)
                        composable(ROUTE_HOME) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (page) {
                                    HOME_PAGE -> {
                                        HomeScreen(
                                            onChatClick = { chatId ->
                                                navController.navigate("chat/$chatId")
                                            },
                                            onNewChat = { chatId ->
                                                navController.navigate("chat/$chatId")
                                            }
                                        )
                                    }
                                    SETTINGS_PAGE -> {
                                        SettingsScreen(
                                            onBack = {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(HOME_PAGE)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Page indicator
                            PageIndicator(
                                currentPage = pagerState.currentPage,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                            )
                        }

                        // Chat screen
                        composable(
                            route = "chat/{chatId}",
                            arguments = listOf(
                                navArgument("chatId") {
                                    type = NavType.StringType
                                    nullable = true
                                }
                            )
                        ) { backStackEntry ->
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
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAGE_COUNT) { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                )
            }
        }
    }
}