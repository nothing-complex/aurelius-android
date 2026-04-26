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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val HOME_PAGE = 0
private const val SETTINGS_PAGE = 1
private const val PAGE_COUNT = 2

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

    // Check if we're on chat screen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isChatScreen = currentRoute?.startsWith("chat") == true

    AureliusTheme(darkTheme = isDark) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (isChatScreen) {
                        // Chat screen - no pager, just NavHost for chat
                        NavHost(
                            navController = navController,
                            startDestination = "chat"
                        ) {
                            composable("chat") {
                                // Placeholder - will be replaced by dynamic route
                            }
                            composable("chat/{chatId}") { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getString("chatId")
                                ChatScreen(
                                    chatId = if (chatId == "new") null else chatId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    } else {
                        // Swipe navigation for Home/Settings
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

                        // Subtle page indicator dots
                        PageIndicator(
                            currentPage = pagerState.currentPage,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
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