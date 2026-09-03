package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.util.HapticHelper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isProbing by viewModel.isProbingEndpoints.collectAsState()
    val endpointProbes by viewModel.endpointProbes.collectAsState()

    var urlInput by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var endpointInput by remember(settings.customEndpoint) { mutableStateOf(settings.customEndpoint) }
    var modelInput by remember(settings.modelName) { mutableStateOf(settings.modelName) }
    var promptInput by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Bar - Minimalist and elegant
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                    onNavigateBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NavySurfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, NavyBorder, CircleShape)
                    .testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Definições",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Rede Termux & Parâmetros",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Network & Termux Server Configuration
            SettingsSectionCard(
                title = "Servidor Termux & Endereço de Rede",
                icon = Icons.Default.Wifi
            ) {
                Text(
                    text = "Endereço URL do Hermes (Porta 9119):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        viewModel.updateServerUrl(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Atalhos de IP e Endereço:",
                    fontSize = 12.sp,
                    color = TextTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickUrlChip(
                        label = "Termux Localhost",
                        url = "http://127.0.0.1:9119/",
                        isSelected = urlInput == "http://127.0.0.1:9119/",
                        onClick = {
                            urlInput = "http://127.0.0.1:9119/"
                            viewModel.updateServerUrl("http://127.0.0.1:9119/")
                            viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                        }
                    )

                    QuickUrlChip(
                        label = "localhost:9119",
                        url = "http://localhost:9119/",
                        isSelected = urlInput == "http://localhost:9119/",
                        onClick = {
                            urlInput = "http://localhost:9119/"
                            viewModel.updateServerUrl("http://localhost:9119/")
                            viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Test Connection Button with Ktor
                Button(
                    onClick = {
                        viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                        viewModel.testConnection()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_connection_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = NavyDeep
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            color = NavyDeep,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("A Testar com Ktor Client...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testar Ligação & Ping Ktor", fontWeight = FontWeight.Bold)
                    }
                }

                // Test Diagnostics Result Card
                if (testResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val isSuccess = testResult?.isReachable == true
                    Surface(
                        color = if (isSuccess) NavySurfaceVariant else NavyDeep,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSuccess) StatusOnline else StatusOffline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (isSuccess) StatusOnline else StatusOffline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSuccess) "Ligação Estabelecida com Sucesso!" else "Falha na Ligação ao Hermes",
                                    color = if (isSuccess) StatusOnline else StatusOffline,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (isSuccess) {
                                Text(
                                    text = "• Código HTTP: ${testResult?.statusCode}\n• Latência: ${testResult?.latencyMs} ms (Ktor OkHttp Engine)\n• Servidor: ${testResult?.serverHeader}\n• usesCleartextTraffic: Ativo e autorizado para HTTP",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = testResult?.errorMessage ?: "Não foi possível ligar ao endereço indicado.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Endpoint Route & Auto-Detection
            SettingsSectionCard(
                title = "Rota da API & Endpoints do Hermes",
                icon = Icons.Default.Terminal
            ) {
                Text(
                    text = "Endpoint / Rota do Servidor Termux:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = {
                        endpointInput = it
                        viewModel.updateCustomEndpoint(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    placeholder = {
                        Text("AUTO ou /v1/chat/completions ou /chat", color = TextTertiary, fontSize = 12.5.sp)
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Rotas Comuns de Servidores:",
                    fontSize = 12.sp,
                    color = TextTertiary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickUrlChip(
                        label = "AUTO (Auto-Detectar)",
                        url = "AUTO",
                        isSelected = endpointInput.equals("AUTO", ignoreCase = true),
                        onClick = {
                            endpointInput = "AUTO"
                            viewModel.updateCustomEndpoint("AUTO")
                        }
                    )

                    QuickUrlChip(
                        label = "/chat",
                        url = "/chat",
                        isSelected = endpointInput == "/chat",
                        onClick = {
                            endpointInput = "/chat"
                            viewModel.updateCustomEndpoint("/chat")
                        }
                    )

                    QuickUrlChip(
                        label = "/generate",
                        url = "/generate",
                        isSelected = endpointInput == "/generate",
                        onClick = {
                            endpointInput = "/generate"
                            viewModel.updateCustomEndpoint("/generate")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickUrlChip(
                        label = "/v1/chat/completions",
                        url = "/v1/chat/completions",
                        isSelected = endpointInput == "/v1/chat/completions",
                        onClick = {
                            endpointInput = "/v1/chat/completions"
                            viewModel.updateCustomEndpoint("/v1/chat/completions")
                        }
                    )

                    QuickUrlChip(
                        label = "/api/chat",
                        url = "/api/chat",
                        isSelected = endpointInput == "/api/chat",
                        onClick = {
                            endpointInput = "/api/chat"
                            viewModel.updateCustomEndpoint("/api/chat")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Endpoint Diagnostic Scan Button
                OutlinedButton(
                    onClick = {
                        viewModel.probeServerEndpoints()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    enabled = !isProbing
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("A Sondar Endpoints no Servidor...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Diagnosticar Rotas do Servidor (Scan)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Show Probed Endpoints
                if (endpointProbes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Resultados da Verificação de Rotas:",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        endpointProbes.forEach { probe ->
                            val isOk = probe.statusCode == 200 || probe.isSuccess
                            val is405 = probe.statusCode == 405
                            Surface(
                                color = NavySurface,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOk) StatusOnline.copy(alpha = 0.6f) else if (is405) StatusWarning.copy(alpha = 0.5f) else NavyBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${probe.method} ${probe.path.ifBlank { "/" }}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOk) StatusOnline else if (is405) StatusWarning else TextPrimary
                                            )
                                        }
                                        Text(
                                            text = "${probe.message} • ${probe.latencyMs}ms",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    if (isOk || probe.statusCode == 422) {
                                        Button(
                                            onClick = {
                                                val path = probe.path.ifBlank { "/" }
                                                endpointInput = path
                                                viewModel.updateCustomEndpoint(path)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDeep)
                                        ) {
                                            Text("Ativar Rota", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Model & LLM Parameters
            SettingsSectionCard(
                title = "Parâmetros do Modelo",
                icon = Icons.Default.Speed
            ) {
                // Temperature Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Temperatura:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "%.2f".format(settings.temperature),
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = settings.temperature,
                    onValueChange = { viewModel.updateTemperature(it) },
                    valueRange = 0.0f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary,
                        inactiveTrackColor = NavySurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Max Tokens Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Limite de Tokens (Max Tokens):",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${settings.maxTokens}",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { viewModel.updateMaxTokens(it.roundToInt()) },
                    valueRange = 256f..8192f,
                    steps = 30,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary,
                        inactiveTrackColor = NavySurfaceVariant
                    )
                )
            }

            // Section 3: Appearance, DPI & Haptics
            SettingsSectionCard(
                title = "Aparência, Resolução (DPI) & Háptica",
                icon = Icons.Default.DisplaySettings
            ) {
                Text(
                    text = "Densidade da Interface (DPI):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ajusta a escala dos elementos gráficos para um aspeto mais nítido, elegante e moderno.",
                    fontSize = 11.5.sp,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Density scale selector chips
                val densityOptions = listOf(
                    Triple("0.88x", "Alta Resolução", 0.88f),
                    Triple("0.94x", "Equilibrada", 0.94f),
                    Triple("1.00x", "Padrão", 1.00f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    densityOptions.forEach { (label, desc, scale) ->
                        val isSelected = kotlin.math.abs(settings.uiDensityScale - scale) < 0.03f
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.updateUiDensityScale(scale)
                                },
                            color = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else NavySurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else NavyBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) GoldPrimary else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 10.5.sp,
                                    color = if (isSelected) GoldAccent else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Haptic feedback toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Vibração ao Interagir na App",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Ativa feedback tátil em botões e envios. (A vibração ao escrever no teclado é controlada pelo sistema do telemóvel).",
                            fontSize = 11.5.sp,
                            color = TextTertiary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = settings.hapticEnabled,
                        onCheckedChange = { viewModel.updateHapticEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NavyDeep,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = NavySurfaceVariant
                        )
                    )
                }
            }

            // Section 4: Reset Actions
            OutlinedButton(
                onClick = {
                    viewModel.resetToDefaults()
                    urlInput = "http://127.0.0.1:9120/"
                    modelInput = "hermes-agent"
                    promptInput = "Tu és o Hermes, um modelo de inteligência artificial de elite a correr localmente no dispositivo via Termux."
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NavyBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar Configurações Padrão")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = NavySurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, NavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }
            content()
        }
    }
}

@Composable
fun QuickUrlChip(
    label: String,
    url: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) GoldContainer else NavySurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isSelected) GoldPrimary else NavyBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                color = if (isSelected) GoldPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
