package com.greyloop.aurelius.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greyloop.aurelius.domain.model.DefaultPersonas
import com.greyloop.aurelius.domain.model.Message
import com.greyloop.aurelius.domain.model.Persona
import com.greyloop.aurelius.domain.model.Role
import com.greyloop.aurelius.ui.components.MarkdownText
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel { parametersOf(chatId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val shouldScrollToBottom by remember {
        derivedStateOf { uiState.messages.isNotEmpty() }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            val result = snackbarHostState.showSnackbar(error)
            if (result == SnackbarResult.Dismissed) {
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.ScrollToBottom -> {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }
                is ChatEvent.Error -> {
                    // Error handled in UI state
                }
                is ChatEvent.BranchCreated -> {
                    // Navigate to the new branch chat
                }
            }
        }
    }

    LaunchedEffect(shouldScrollToBottom) {
        if (shouldScrollToBottom) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Swipe-back state
    var offsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val swipeThreshold = 120.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > swipeThreshold.toPx()) {
                            onBack()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                    }
                )
            }
            .graphicsLayer { translationX = offsetX * 0.3f }
    ) {
        // Floating "New chat" pill — positioned top-left, minimal
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            onClick = onBack
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = uiState.chat?.title ?: "New chat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Persona selector — floating pill, top-right
        if (uiState.chat != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp)
                    .clickable { viewModel.togglePersonaSelector() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
            ) {
                Text(
                    text = uiState.currentPersona.name,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Persona selector dropdown
            DropdownMenu(
                expanded = uiState.showPersonaSelector,
                onDismissRequest = { viewModel.togglePersonaSelector() }
            ) {
                DefaultPersonas.all.forEach { persona ->
                    DropdownMenuItem(
                        text = { Text(persona.name) },
                        onClick = { viewModel.onPersonaSelected(persona) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Messages list — single-column centered with generous vertical rhythm
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onBranchClick = { messageId -> viewModel.onBranchConversation(messageId) },
                        showThinkingTags = uiState.showThinkingTags
                    )
                }

                // Streaming indicator
                if (uiState.isLoading && uiState.streamingContent.isNotEmpty()) {
                    item {
                        val thinkingContent = if (uiState.showThinkingTags) extractThinkingContent(uiState.streamingContent) else null
                        StreamingMessageBubble(
                            content = stripThinkingContent(uiState.streamingContent),
                            thinkingContent = thinkingContent
                        )
                    }
                }

                // Loading indicator
                if (uiState.isLoading && uiState.streamingContent.isEmpty()) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Quick action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(onClick = { /* Image picker */ }) {
                    Icon(Icons.Default.Image, contentDescription = "Add image")
                }
                FilledTonalIconButton(onClick = { /* Voice input */ }) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Add music")
                }
                FilledTonalIconButton(onClick = { /* Web search */ }) {
                    Icon(Icons.Default.Search, contentDescription = "Web search")
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusable(),
                    placeholder = { Text("Type a message...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (uiState.inputText.isNotEmpty() && !uiState.isLoading) {
                                viewModel.sendMessage()
                            }
                        }
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        if (uiState.inputText.isNotEmpty() && !uiState.isLoading) {
                            viewModel.sendMessage()
                        }
                    },
                    enabled = uiState.inputText.isNotEmpty() && !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    onBranchClick: (String) -> Unit,
    showThinkingTags: Boolean = true
) {
    val isUser = message.role == Role.USER

    // Concept A Parchment Scroll — aged parchment user bubbles, sage AI bubbles
    val userBubbleColor = com.greyloop.aurelius.ui.theme.AgedParchment
    val aiBubbleColor = com.greyloop.aurelius.ui.theme.LetterSage
    val borderColor = com.greyloop.aurelius.ui.theme.ScrollBorder
    val userTextColor = com.greyloop.aurelius.ui.theme.ParchmentInk

    AnimatedVisibility(
        visible = true,
        enter = if (isUser) {
            slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = spring(stiffness = 100f, dampingRatio = 20f))
        } else {
            slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = spring(stiffness = 100f, dampingRatio = 20f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (isUser) onBranchClick(message.id) }
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Thinking tag footnote (Ghost Footnote style)
            if (!isUser && showThinkingTags) {
                val thinkingContent = extractThinkingContent(message.content)
                if (thinkingContent != null) {
                    ThinkingTagFootnote(thinkingContent = thinkingContent)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) {
                    userBubbleColor
                } else {
                    aiBubbleColor
                },
                shadowElevation = 4.dp,
                border = BorderStroke(2.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = userTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        val displayContent = stripThinkingContent(message.content)
                        MarkdownText(
                            text = displayContent,
                            modifier = Modifier,
                            color = Color.White
                        )
                    }

                    // Media attachments
                    message.imageUrl?.let { url ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "[Image: $url]",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    message.audioUrl?.let { url ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "[Audio: $url]",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) {
                                Color.White.copy(alpha = 0.7f)
                            } else {
                                Color.White.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
        }
    }
}

/**
 * Extracts thinking content from message content.
 * Handles both XML format (<think>/</think>) and HTML-like format (<think>/</think>).
 * Also handles 2-newline and 3-newline marker formats.
 * Returns null if no thinking content is found.
 */
private fun extractThinkingContent(content: String): String? {
    // Try XML format first: <think> ... </think>
    val xmlStart2 = "\n\n<think>"
    val xmlStart3 = "\n\n\n<think>"
    val xmlEnd = "</think>"

    val xmlIdx2 = content.indexOf(xmlStart2)
    val xmlIdx3 = content.indexOf(xmlStart3)
    val xmlIdx = if (xmlIdx2 >= 0 && (xmlIdx3 < 0 || xmlIdx2 < xmlIdx3)) xmlIdx2 else xmlIdx3
    val xmlStartLen = if (xmlIdx2 >= 0 && (xmlIdx3 < 0 || xmlIdx2 < xmlIdx3)) xmlStart2.length else xmlStart3.length

    if (xmlIdx >= 0) {
        val endIdx = content.indexOf(xmlEnd, xmlIdx)
        if (endIdx >= 0) {
            return content.substring(xmlIdx + xmlStartLen, endIdx).trim()
        }
    }

    // Try HTML-like format: <think>...</think>
    val htmlStart = "<think>"
    val htmlEnd = "</think>"
    val htmlIdx = content.indexOf(htmlStart)
    if (htmlIdx >= 0) {
        val endIdx = content.indexOf(htmlEnd, htmlIdx)
        if (endIdx >= 0) {
            return content.substring(htmlIdx + htmlStart.length, endIdx).trim()
        }
    }

    return null
}

/**
 * Strips thinking content from message content for display.
 * Handles XML format (<think>/</think>) and HTML-like format (<think>/</think>).
 */
private fun stripThinkingContent(content: String): String {
    var result = content

    // Strip XML format: <think> ... </think> (2-newline or 3-newline variants)
    val xmlStart2 = "\n\n<think>"
    val xmlStart3 = "\n\n\n<think>"
    val xmlEnd = "</think>"

    val xmlIdx2 = result.indexOf(xmlStart2)
    val xmlIdx3 = result.indexOf(xmlStart3)
    val xmlIdx = if (xmlIdx2 >= 0 && (xmlIdx3 < 0 || xmlIdx2 < xmlIdx3)) xmlIdx2 else xmlIdx3
    val xmlStartLen = if (xmlIdx2 >= 0 && (xmlIdx3 < 0 || xmlIdx2 < xmlIdx3)) xmlStart2.length else xmlStart3.length

    if (xmlIdx >= 0) {
        val endIdx = result.indexOf(xmlEnd, xmlIdx)
        if (endIdx >= 0) {
            val before = result.substring(0, xmlIdx)
            val after = result.substring(endIdx + xmlEnd.length).trimStart('\n')
            result = (before + after).trim()
        }
    }

    // Strip HTML-like format: <think>...</think>
    val htmlStart = "<think>"
    val htmlEnd = "</think>"
    val htmlIdx = result.indexOf(htmlStart)
    if (htmlIdx >= 0) {
        val endIdx = result.indexOf(htmlEnd, htmlIdx)
        if (endIdx >= 0) {
            val before = result.substring(0, htmlIdx)
            val after = result.substring(endIdx + htmlEnd.length).trimStart('\n')
            result = (before + after).trim()
        }
    }

    return result
}

/**
 * Ghost Footnote — displays AI reasoning above responses.
 * Style: 8dp above AI bubble, 40%/50% opacity, Source Sans 3 italic 12sp,
 * LetterSage at 60% opacity, dashed top border in ScrollBorder.
 */
@Composable
private fun ThinkingTagFootnote(thinkingContent: String) {
    val letterSageColor = com.greyloop.aurelius.ui.theme.LetterSage
    val scrollBorderColor = com.greyloop.aurelius.ui.theme.ScrollBorder

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = letterSageColor.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, scrollBorderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = thinkingContent,
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = letterSageColor.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
private fun StreamingMessageBubble(content: String, thinkingContent: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        Column {
            if (thinkingContent != null) {
                ThinkingTagFootnote(thinkingContent = thinkingContent)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Streaming...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha3"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha1 }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha2 }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha3 }
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
