package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.TextPrimary

data class QuickPrompt(
    val title: String,
    val prompt: String,
    val icon: ImageVector
)

val defaultQuickPrompts = listOf(
    QuickPrompt(
        title = "Estado Hermes",
        prompt = "Olá Hermes! Podes confirmar os teus parâmetros de sistema e estado no servidor local?",
        icon = Icons.Default.Info
    ),
    QuickPrompt(
        title = "Comandos Termux",
        prompt = "Quais são os comandos úteis para gerir serviços e pacotes dentro do proot no Termux?",
        icon = Icons.Default.Terminal
    ),
    QuickPrompt(
        title = "Gerar Script Python",
        prompt = "Escreve um script Python eficiente com docstrings e type hints para processar dados.",
        icon = Icons.Default.Code
    ),
    QuickPrompt(
        title = "Benchmark Rápido",
        prompt = "Conta de 1 a 10 e explica brevemente como o teu modelo optimiza o raciocínio.",
        icon = Icons.Default.Speed
    ),
    QuickPrompt(
        title = "Resumo Criativo",
        prompt = "Explica o funcionamento de Large Language Models locais de forma clara e concisa.",
        icon = Icons.Default.AutoAwesome
    )
)

@Composable
fun QuickPromptRow(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        defaultQuickPrompts.forEach { item ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(NavySurfaceCard)
                    .border(1.dp, NavyBorder, RoundedCornerShape(16.dp))
                    .clickable { onPromptSelected(item.prompt) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
