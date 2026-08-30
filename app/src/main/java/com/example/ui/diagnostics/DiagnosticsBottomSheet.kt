package com.example.ui.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ServerHealth
import com.example.ui.components.CodeBlockCard
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    serverHealth: ServerHealth?,
    serverUrl: String,
    onDismiss: () -> Unit,
    onRecheck: () -> Unit,
    sheetState: SheetState
) {
    val isOnline = serverHealth?.isReachable == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyDeep,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) StatusOnline.copy(alpha = 0.2f) else StatusOffline.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isOnline) StatusOnline else StatusOffline,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Diagnóstico do Servidor Hermes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ktor Client HTTP Engine",
                            fontSize = 12.sp,
                            color = GoldAccent
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                }
            }

            // Connection Details Card
            Surface(
                color = NavySurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticRow(
                        label = "Endereço Base",
                        value = serverUrl,
                        isCode = true
                    )
                    DiagnosticRow(
                        label = "Estado da Ligação",
                        value = if (isOnline) "Conectado (Online)" else "Desconectado (Offline)",
                        valueColor = if (isOnline) StatusOnline else StatusOffline
                    )
                    DiagnosticRow(
                        label = "Latência Round-trip",
                        value = if (serverHealth != null) "${serverHealth.latencyMs} ms" else "N/A",
                        isCode = true
                    )
                    DiagnosticRow(
                        label = "Código HTTP",
                        value = if (serverHealth != null && serverHealth.statusCode > 0) "${serverHealth.statusCode}" else "N/A",
                        isCode = true
                    )
                    DiagnosticRow(
                        label = "Cleartext HTTP",
                        value = "Autorizado (Manifest usesCleartextTraffic)",
                        valueColor = GoldAccent
                    )
                }
            }

            // Termux Command Assistance
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Como iniciar o servidor no Termux (proot):",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }

                CodeBlockCard(
                    language = "bash",
                    code = "# No Termux (dentro do proot):\nhermes server --port 9119\n\n# Ou com python/uvicorn:\npython -m uvicorn main:app --host 0.0.0.0 --port 9119"
                )
            }

            // Action Button
            Button(
                onClick = onRecheck,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDeep)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Repetir Verificação", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isCode: Boolean = false,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 12.5.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default
        )
    }
}
