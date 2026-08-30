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

    var urlInput by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
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
        // Header
        Surface(
            color = NavyDeep,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        viewModel.hapticHelper.trigger(HapticHelper.HapticType.CLICK, settings.hapticEnabled)
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = GoldPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Definições do Hermes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Configuração de Rede e Servidor Termux",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
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

            // Section 2: Cleartext HTTP & Android Security
            SettingsSectionCard(
                title = "Segurança de Rede Android",
                icon = Icons.Default.Security
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GoldContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "usesCleartextTraffic = true",
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Permite pedidos HTTP não encriptados para a porta local 9119 no Termux sem ser bloqueado pelo Android.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Section 3: Model & LLM Parameters
            SettingsSectionCard(
                title = "Parâmetros do Modelo Hermes",
                icon = Icons.Default.Speed
            ) {
                Text(
                    text = "Identificador do Modelo:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = modelInput,
                    onValueChange = {
                        modelInput = it
                        viewModel.updateModelName(it)
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
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(10.dp))

                // System Prompt
                Text(
                    text = "Prompt de Sistema:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = {
                        promptInput = it
                        viewModel.updateSystemPrompt(it)
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
                    maxLines = 4
                )
            }

            // Section 4: Galaxy S26 Ultra & Tactile UX
            SettingsSectionCard(
                title = "Otimizações Galaxy S26 Ultra",
                icon = Icons.Default.DisplaySettings
            ) {
                // Haptic Feedback Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Feedback Tátil de Precisão",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Vibrações hápticas táteis ao digitar, enviar e receber respostas",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Switch(
                        checked = settings.hapticEnabled,
                        onCheckedChange = { viewModel.updateHapticEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NavyDeep,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = NavySurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // S Pen Optimization Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Suporte a Entrada por S Pen",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Deteção de stylus, efeito hover e caligrafia no ecrã do S26 Ultra",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Switch(
                        checked = settings.sPenModeEnabled,
                        onCheckedChange = { viewModel.updateSPenModeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NavyDeep,
                            checkedTrackColor = GoldPrimary,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = NavySurfaceVariant
                        )
                    )
                }
            }

            // Section 5: Reset Actions
            OutlinedButton(
                onClick = {
                    viewModel.resetToDefaults()
                    urlInput = "http://127.0.0.1:9119/"
                    modelInput = "hermes-3"
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
