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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.data.MessageSender
import com.example.data.MessageStatus
import com.example.data.ProfileDto
import com.example.ui.components.MarkdownMessageView
import com.example.ui.components.QuickPromptRow
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
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
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
    // remember (não Saveable): o estado do dialog não deve sobreviver a navegação
    // para Settings e voltar — era isso que reabria a janela ao andar para trás.
    var showProfileDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Abre o dialog de perfis quando o ViewModel pede (chip /profile ou comando escrito)
    val profileDialogEvent by viewModel.profileDialogEvent.collectAsState()
    LaunchedEffect(profileDialogEvent) {
        if (profileDialogEvent > 0) {
            viewModel.refreshProfileInfo()
            showProfileDialog = true
            // Consome o evento para não reabrir ao voltar das Settings
            viewModel.consumeProfileDialogEvent()
        }
    }

    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NavyDeep,
                drawerContentColor = TextPrimary,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Conversas",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )

                        IconButton(
                            onClick = {
                                viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                                viewModel.createNewSession()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Novo Chat",
                                tint = NavyDeep,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = NavyBorderSubtle)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == currentSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NavySurfaceVariant else Color.Transparent)
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = if (isSelected) GoldPrimary else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = session.title,
                                        color = if (isSelected) GoldPrimary else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }

                                if (sessions.size > 1) {
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteSession(session.id)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Apagar",
                                            tint = TextTertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = NavyBorderSubtle)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                onNavigateToSettings()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                        Text("Definições", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    ) {
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
                    agentName = agentName,
                    serverHealth = serverHealth,
                    onNavigationDrawerOpen = {
                        scope.launch { drawerState.open() }
                    },
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
                            text = { Text("Novo Chat", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Add, null, tint = GoldAccent) },
                            onClick = {
                                showMenu = false
                                viewModel.createNewSession()
                            }
                        )
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
                            text = { Text("Mudar Perfil / Agente", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = GoldAccent) },
                            onClick = {
                                showMenu = false
                                viewModel.refreshProfileInfo()
                                showProfileDialog = true
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
                            text = { Text("Limpar Conversa Atual", color = StatusOffline) },
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

                // Painel ao vivo: pensamento do modelo + ferramentas em uso
                val liveThinking by viewModel.liveThinking.collectAsState()
                val liveToolUse by viewModel.liveToolUse.collectAsState()
                val finalThinking by viewModel.finalThinking.collectAsState()
                if (isGenerating && (!liveThinking.isNullOrBlank() || !liveToolUse.isNullOrBlank())) {
                    LiveStatusPanel(
                        thinking = liveThinking?.takeLast(600),
                        toolUse = liveToolUse
                    )
                } else if (!isGenerating && !finalThinking.isNullOrBlank()) {
                    LiveStatusPanel(
                        thinking = finalThinking?.takeLast(1000),
                        toolUse = null
                    )
                }

                // Input Bar com Badge Corrigido
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
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpar Conversa?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Todas as mensagens desta conversa serão apagadas localmente.", color = TextSecondary) },
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

    if (showProfileDialog) {
        val profilesToShow = if (availableProfiles.isNotEmpty()) {
            availableProfiles
        } else {
            listOf(
                ProfileDto(id = "default", name = "Agent T", active = (agentName == null || agentName == "Agent T")),
                ProfileDto(id = "tara", name = "Tara", active = (agentName == "Tara"))
            )
        }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Escolher Perfil / Agente",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profilesToShow.forEach { prof ->
                        val isSelected = prof.active || (agentName != null && prof.name.equals(agentName, ignoreCase = true))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GoldPrimary.copy(alpha = 0.15f) else NavySurfaceVariant)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) GoldPrimary else NavyBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                                    viewModel.switchProfile(prof.id, prof.name)
                                    showProfileDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prof.name,
                                    color = if (isSelected) GoldPrimary else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "ID: ${prof.id}",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Ativo",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Fechar", color = TextSecondary)
                }
            },
            containerColor = NavyDeep,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun ChatTopBar(
    agentName: String?,
    serverHealth: com.example.data.ServerHealth?,
    onNavigationDrawerOpen: () -> Unit,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onNavigationDrawerOpen,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NavySurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Conversas",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.clickable {
                        onRefreshClick()
                        onStatusClick()
                    }
                ) {
                    Text(
                        text = agentName ?: "Hermes Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Definições",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Mais Opções",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    availableProfiles: List<ProfileDto>,
    onSelectProfile: (String, String) -> Unit,
    hapticHelper: HapticHelper,
    hapticEnabled: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val context = LocalContext.current

    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.padding(bottom = 3.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                contentDescription = null,
                tint = if (isUser) GoldPrimary else GoldAccent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isUser) "Tu" else "Hermes",
                color = if (isUser) GoldPrimary else GoldAccent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.95f)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 2.dp,
                        topEnd = if (isUser) 2.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(if (isUser) UserBubbleBg else HermesBubbleBg)
                .then(
                    if (isUser) {
                        Modifier.border(
                            BorderStroke(1.dp, UserBubbleBorder),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 2.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                    } else {
                        Modifier.border(
                            BorderStroke(1.dp, HermesBubbleBorder),
                            RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                        )
                    }
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (message.text.length > 50) 60.dp else 36.dp)
                            .background(GoldPrimary, RoundedCornerShape(2.dp))
                    )
                }

                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (message.status == MessageStatus.SENDING && !isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = GoldPrimary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "A iniciar resposta...",
                                color = GoldAccent,
                                fontSize = 13.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    } else if (message.status == MessageStatus.STREAMING && !isUser) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MarkdownMessageView(
                                text = message.text,
                                textColor = TextSecondary,
                                reasoning = message.reasoning,
                                isStreaming = true
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
                                    text = if (message.text.isNotBlank()) "A responder..." else "A raciocinar...",
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
                                textColor = TextSecondary,
                                reasoning = message.reasoning,
                                isStreaming = false
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

            if (message.status == MessageStatus.SENT) {
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Hermes Message", message.text))
                            hapticHelper.trigger(HapticHelper.HapticType.LIGHT_TICK, hapticEnabled)
                            Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
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
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
fun LiveStatusPanel(thinking: String?, toolUse: String?, modifier: Modifier = Modifier) {
    Surface(
        color = NavySurfaceCard,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (!toolUse.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔧", fontSize = 13.sp)
                    Text(
                        text = toolUse,
                        color = GoldAccent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            if (!thinking.isNullOrBlank()) {
                if (!toolUse.isNullOrBlank()) Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Pensamento em direto",
                        tint = GoldPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Pensamento em direto",
                        color = GoldPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = thinking.takeLast(1000) + " ▎",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 6,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val hasStylus = event.changes.any { it.type == PointerType.Stylus }
                                    onSPenHover(hasStylus)
                                }
                            }
                        },
                    placeholder = {
                        Text(
                            text = if (isSPenHovering) "Escrever ou usar S Pen..." else "Mensagem para Hermes...",
                            color = if (isSPenHovering) GoldAccent else TextTertiary,
                            fontSize = 14.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        cursorColor = GoldPrimary,
                        focusedBorderColor = if (isSPenHovering) GoldAccent else GoldPrimary,
                        unfocusedBorderColor = NavyBorder
                    ),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    singleLine = false,
                    maxLines = 4,
                    minLines = 1
                )

                Box(
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = if (inputText.isNotBlank()) 6.dp else 0.dp,
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
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isGenerating) "Enviar (fica em fila)" else "Enviar Mensagem",
                            tint = if (inputText.isNotBlank()) NavyDeep else TextTertiary,
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.Center)
                        )
                    }

                    if (pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(NavyDeep)
                                .border(1.5.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${if (pendingCount > 9) "9+" else pendingCount}",
                                color = GoldPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
