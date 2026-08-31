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
        title = "O que sabes de mim?",
        prompt = "O que sabes sobre mim? Dá um resumo do meu perfil e da nossa conversa até agora.",
        icon = Icons.Default.Info
    ),
    QuickPrompt(
        title = "Dica de guitarra",
        prompt = "Dá-me um exercício rápido para melhorar a transição entre acordes na guitarra clássica, focado no meu repertório atual.",
        icon = Icons.Default.AutoAwesome
    ),
    QuickPrompt(
        title = "Estado do Hermes",
        prompt = "Mostra um resumo do estado atual do Hermes: portas, processos, skills carregadas e memória.",
        icon = Icons.Default.Terminal
    ),
    QuickPrompt(
        title = "Resume esta conversa",
        prompt = "Faz um resumo conciso dos pontos principais que tratámos nesta conversa.",
        icon = Icons.Default.Code
    ),
    QuickPrompt(
        title = "Sugestão rápida",
        prompt = "Dá-me uma ideia ou sugestão aleatória útil para o que estou a fazer agora.",
        icon = Icons.Default.Speed
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
