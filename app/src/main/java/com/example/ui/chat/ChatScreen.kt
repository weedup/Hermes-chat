package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.MessageSender
import com.example.data.MessageStatus
import com.example.data.ProfileDto
import com.example.ui.components.MarkdownMessageView
import com.example.ui.components.QuickPromptRow
import com.example.ui.components.ServerStatusBadge
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.HermesBubbleBg
import com.example.ui.theme.HermesBubbleBorder
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyBorderSubtle
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.UserBubbleBg
import com.example.ui.theme.UserBubbleBorder
import com.example.ui.theme.UserBubbleText
import com.example.util.HapticHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val serverHealth by viewModel.serverHealth.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isSPenHovering by viewModel.isSPenHovering.collectAsState()
    val agentName by viewModel.agentName.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header Bar
            ChatTopBar(
                serverUrl = settings.serverUrl,
                modelName = settings.modelName,
                agentName = agentName,
                serverHealth = serverHealth,
                onStatusClick = onOpenDiagnostics,
                onRefreshClick = {
                    viewModel.hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK, settings.hapticEnabled)
                    viewModel.checkServerHealth()
                },
                onSettingsClick = {
                    viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                    onNavigateToSettings()
                },
                onMenuClick = { showMenu = true }
            )

            // Dropdown Menu for TopBar
            Box(modifier = Modifier.fillMaxWidth()) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(NavySurfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Verificar Ligação Termux", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, tint = GoldAccent) },
                        onClick = {
                            showMenu = false
                            viewModel.checkServerHealth()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Diagnósticos do Servidor", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Terminal, null, tint = GoldAccent) },
                        onClick = {
                            showMenu = false
                            onOpenDiagnostics()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Definições do Servidor", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Settings, null, tint = GoldAccent) },
                        onClick = {
                            showMenu = false
                            onNavigateToSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Limpar Conversa", color = StatusOffline) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = StatusOffline) },
                        onClick = {
                            showMenu = false
                            showClearDialog = true
                        }
                    )
                }
            }

            // Chat Messages or Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyChatState(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                availableProfiles = availableProfiles,
                                onSelectProfile = { profId, profName ->
                                    viewModel.switchProfile(profId, profName)
                                },
                                hapticHelper = viewModel.hapticHelper,
                                hapticEnabled = settings.hapticEnabled,
                                onRetry = { viewModel.retryMessage(message.id) },
                                onDelete = { viewModel.deleteMessage(message.id) },
                                onOpenSettings = onNavigateToSettings
                            )
                        }
                    }
                }
            }

            // Barra de Prompts e Comandos Deslizante Unificada
            QuickPromptRow(
                onPromptSelected = { prompt ->
                    viewModel.hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK, settings.hapticEnabled)
                    viewModel.sendMessage(prompt)
                }
            )

            // "A pensar..." indicator + fila de mensagens em espera
            AnimatedVisibility(
                visible = isGenerating || pendingCount > 0,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                ThinkingBar(
                    isGenerating = isGenerating,
                    agentName = agentName,
                    pendingCount = pendingCount
                )
            }

            // Input Bar optimized for Galaxy S26 Ultra & S Pen
            ChatInputBar(
                inputText = inputText,
                isGenerating = isGenerating,
                pendingCount = pendingCount,
                isSPenHovering = isSPenHovering,
                hapticEnabled = settings.hapticEnabled,
                sPenEnabled = settings.sPenModeEnabled,
                hapticHelper = viewModel.hapticHelper,
                onTextChanged = viewModel::onInputChanged,
                onSend = { viewModel.sendMessage() },
                onSPenHover = viewModel::setSPenHover
            )
        }
    }

    // Confirmation dialog for clearing chat
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpar Conversa?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens locais do Hermes Chat serão apagadas.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearDialog = false
                    }
                ) {
                    Text("Apagar", color = StatusOffline, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = NavySurfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ChatTopBar(
    serverUrl: String,
    modelName: String,
    agentName: String?,
    serverHealth: com.example.data.ServerHealth?,
    onStatusClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = serverHealth?.isReachable == true

    Surface(
        color = NavyDeep,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, NavyBorderSubtle))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand: Gold square badge with "H" & Termux status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onStatusClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp), ambientColor = GoldPrimary, spotColor = GoldPrimary)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "H",
                        color = NavyDeep,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = agentName ?: "Hermes Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) StatusOnline else StatusOffline)
                        )

                        Text(
                            text = if (isOnline) "TERMUX LOCALHOST: 9120" else "TERMUX OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp,
                            color = if (isOnline) StatusOnline else StatusOffline
                        )
                    }
                }
            }

            // Actions & Status Buttons in Sleek rounded circles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick ping refresh button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NavySurfaceCard)
                        .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                        .clickable { onRefreshClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Ping Servidor",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NavySurfaceCard)
                        .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                        .clickable { onSettingsClick() }
                        .testTag("settings_top_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Definições",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Menu button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NavySurfaceCard)
                        .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    availableProfiles: List<ProfileDto> = emptyList(),
    onSelectProfile: (String, String) -> Unit = { _, _ -> },
    hapticHelper: HapticHelper,
    hapticEnabled: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = message.sender == MessageSender.USER
    val isSystemNotice = message.sender == MessageSender.SYSTEM
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    if (isSystemNotice) {
        val isProfileSelection = message.text.contains("Escolhe o Perfil Hermes")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = GoldPrimary.copy(alpha = 0.9f),
                modifier = Modifier
                    .background(
                        HermesBubbleBg.copy(alpha = 0.7f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        BorderStroke(1.dp, HermesBubbleBorder),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            if (isProfileSelection && availableProfiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableProfiles.forEach { prof ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (prof.active) GoldPrimary.copy(alpha = 0.2f) else NavySurfaceCard)
                                .border(
                                    1.dp,
                                    if (prof.active) GoldPrimary else NavyBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectProfile(prof.id, prof.name)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (prof.active) GoldPrimary else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = prof.name,
                                color = if (prof.active) GoldPrimary else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (prof.active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .shadow(
                    elevation = if (isUser) 4.dp else 2.dp,
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 2.dp,
                        topEnd = if (isUser) 2.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    ambientColor = if (isUser) GoldPrimary else Color.Black,
                    spotColor = if (isUser) GoldPrimary else Color.Black
                )
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 2.dp,
                        topEnd = if (isUser) 2.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(
                    if (isUser) UserBubbleBg else HermesBubbleBg
                )
                .then(
                    if (!isUser) {
                        Modifier.border(
                            BorderStroke(1.dp, HermesBubbleBorder),
                            RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                    } else Modifier
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Hermes left gold accent line (border-l-2 in Sleek theme)
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (message.text.length > 50) 60.dp else 36.dp)
                            .background(GoldPrimary, RoundedCornerShape(2.dp))
                    )
                }

                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (message.status == MessageStatus.SENDING && !isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = GoldPrimary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Hermes está a processar no Termux...",
                                color = GoldAccent,
                                fontSize = 13.5.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else if (message.status == MessageStatus.STREAMING && !isUser) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarkdownMessageView(
                                text = message.text,
                                textColor = TextSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = GoldPrimary,
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "A receber...",
                                    color = GoldAccent,
                                    fontSize = 11.5.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    } else if (message.status == MessageStatus.ERROR) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SyncProblem,
                                    contentDescription = "Erro",
                                    tint = StatusOffline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Falha no Hermes Termux",
                                    color = StatusOffline,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = message.text,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onRetry,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tentar Novamente", color = GoldPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        if (isUser) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    text = message.text,
                                    color = UserBubbleText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            MarkdownMessageView(
                                text = message.text,
                                textColor = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Subtitle / Timestamp with metadata and copy action
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (!isUser) "$timeFormatted • Hermes AI" else timeFormatted,
                color = TextTertiary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )

            if (!isUser && message.latencyMs > 0) {
                Text(text = "•", color = TextTertiary, fontSize = 10.sp)
                Text(
                    text = "${message.latencyMs}ms",
                    color = GoldAccent,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (isUser) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }

            // Quick copy action with comfortable touch target
            if (message.status == MessageStatus.SENT) {
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Hermes Message", message.text))
                            hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK, hapticEnabled)
                            Toast.makeText(context, "Texto copiado para a área de transferência", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copiar Mensagem",
                        tint = GoldAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Copiar",
                        color = GoldAccent.copy(alpha = 0.8f),
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(GoldPrimary.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                .border(2.dp, GoldPrimary, CircleShape)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Hermes Chat Local",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Ligação ao servidor Hermes no Termux",
            fontSize = 13.5.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Escreve uma mensagem para começar a conversar.",
            fontSize = 12.5.sp,
            color = TextTertiary
        )
    }
}

@Composable
fun ThinkingBar(
    isGenerating: Boolean,
    agentName: String?,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_alpha"
    )

    Surface(
        color = NavyDeep,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, NavyBorderSubtle))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = GoldPrimary,
                strokeWidth = 2.dp
            )
            Text(
                text = when {
                    isGenerating -> "${agentName ?: "Hermes"} está a pensar…"
                    pendingCount > 0 -> "$pendingCount mensage${if (pendingCount == 1) "m" else "ns"} em espera"
                    else -> ""
                },
                color = TextSecondary.copy(alpha = alpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    isGenerating: Boolean,
    pendingCount: Int,
    isSPenHovering: Boolean,
    hapticEnabled: Boolean,
    sPenEnabled: Boolean,
    hapticHelper: HapticHelper,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSPenHover: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavyDeep,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, NavyBorderSubtle))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek Outer Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(NavySurfaceCard.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        if (isSPenHovering) GoldPrimary else NavyBorder,
                        RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // S Pen / Stylus button
                if (sPenEnabled) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp, end = 2.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSPenHovering) GoldContainer else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = "S Pen Ready",
                            tint = if (isSPenHovering) GoldPrimary else GoldPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Text Input Field inside the pill
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        if (hapticEnabled && newText.length != inputText.length) {
                            hapticHelper.trigger(HapticHelper.HapticType.KEYPRESS)
                        }
                        onTextChanged(newText)
                    },
                    placeholder = {
                        Text(
                            text = if (isSPenHovering) "Escreve com a S Pen..." else "Escreve com a S Pen ou teclado...",
                            color = TextTertiary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 130.dp)
                        .testTag("chat_input_field")
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val isStylus = event.changes.any { it.type == PointerType.Stylus }
                                    onSPenHover(isStylus)
                                }
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = GoldPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK, hapticEnabled)
                                    onTextChanged("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar texto",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSend()
                            }
                        }
                    ),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Sleek Circular Action Button (Gold / Navy with shadow)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(
                            elevation = if (inputText.isNotBlank() || isGenerating) 6.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = GoldPrimary,
                            spotColor = GoldPrimary
                        )
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank()) GoldPrimary else NavySurfaceVariant
                        )
                        .clickable(enabled = inputText.isNotBlank()) {
                            hapticHelper.trigger(HapticHelper.HapticType.HEAVY_CLICK, hapticEnabled)
                            onSend()
                        }
                        .testTag("send_message_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (pendingCount > 0) {
                        // Badge com o número de mensagens em fila
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${if (pendingCount > 9) "9+" else pendingCount}",
                                color = NavyDeep,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isGenerating) "Enviar (fica em fila)" else "Enviar Mensagem",
                        tint = if (inputText.isNotBlank()) NavyDeep else TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sleek bottom indicator bar
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GoldPrimary.copy(alpha = 0.5f))
            )
        }
    }
}
