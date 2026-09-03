package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyBorderSubtle
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MarkdownMessageView(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary,
    reasoning: String? = null,
    isStreaming: Boolean = false
) {
    val thoughtData = remember(text) { extractThoughtAndResponse(text) }
    val effectiveThought = reasoning?.takeIf { it.isNotBlank() } ?: thoughtData.thought
    val mainText = if (reasoning != null && reasoning.isNotBlank()) text else thoughtData.cleanResponse

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Se houver pensamento, apresenta dropdown colapsável
        effectiveThought?.let { thoughtText ->
            ThoughtDropdownCard(
                thought = thoughtText,
                isStreaming = isStreaming && mainText.isBlank()
            )
        }

        // Conteúdo Principal da Resposta
        if (mainText.isNotBlank()) {
            val blocks = remember(mainText) { parseMarkdownBlocks(mainText) }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    blocks.forEach { block ->
                        when (block) {
                            is MarkdownBlock.CodeBlock -> {
                                CodeBlockCard(language = block.language, code = block.code)
                            }
                            is MarkdownBlock.Paragraph -> {
                                FormattedParagraph(content = block.text, textColor = textColor)
                            }
                            is MarkdownBlock.BulletItem -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "• ",
                                        color = GoldPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    FormattedParagraph(content = block.text, textColor = textColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThoughtDropdownCard(
    thought: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: if (isStreaming) true else false

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavySurfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (isStreaming) GoldPrimary.copy(alpha = 0.6f) else NavyBorderSubtle,
                RoundedCornerShape(8.dp)
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { userToggled = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Pensamento",
                        tint = if (isStreaming) GoldPrimary else GoldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isStreaming) "A raciocinar em tempo real..." else (if (expanded) "Processo de Pensamento" else "Ver raciocínio..."),
                        color = if (isStreaming) GoldPrimary else GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isStreaming) {
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = GoldAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavySurfaceCard.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = if (isStreaming) thought.trim() + " ▎" else thought.trim(),
                            color = TextTertiary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }
    }
}

data class ThoughtExtraction(
    val thought: String?,
    val cleanResponse: String
)

fun extractThoughtAndResponse(raw: String): ThoughtExtraction {
    val thinkOpen = "<think>"
    val thinkClose = "</think>"
    val openIdx = raw.indexOf(thinkOpen)
    if (openIdx != -1) {
        val closeIdx = raw.indexOf(thinkClose, openIdx + thinkOpen.length)
        if (closeIdx != -1) {
            val thought = raw.substring(openIdx + thinkOpen.length, closeIdx).trim()
            val before = raw.substring(0, openIdx).trim()
            val after = raw.substring(closeIdx + thinkClose.length).trim()
            val clean = if (before.isNotEmpty() && after.isNotEmpty()) "$before\n\n$after" else "$before$after"
            return ThoughtExtraction(thought = thought, cleanResponse = clean)
        } else {
            // Em streaming ainda não fechou </think>
            val thought = raw.substring(openIdx + thinkOpen.length).trim()
            val before = raw.substring(0, openIdx).trim()
            return ThoughtExtraction(thought = thought, cleanResponse = before)
        }
    }
    return ThoughtExtraction(thought = null, cleanResponse = raw)
}

@Composable
private fun FormattedParagraph(content: String, textColor: Color) {
    val annotated = remember(content, textColor) {
        buildAnnotatedString {
            var i = 0
            while (i < content.length) {
                // Check bold **bold**
                if (i + 1 < content.length && content[i] == '*' && content[i + 1] == '*') {
                    val endBold = content.indexOf("**", i + 2)
                    if (endBold != -1) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        ) {
                            append(content.substring(i + 2, endBold))
                        }
                        i = endBold + 2
                        continue
                    }
                }

                // Check inline code `code`
                if (content[i] == '`') {
                    val endCode = content.indexOf('`', i + 1)
                    if (endCode != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = NavySurfaceVariant,
                                color = GoldAccent,
                                fontSize = 13.5.sp
                            )
                        ) {
                            append(" ${content.substring(i + 1, endCode)} ")
                        }
                        i = endCode + 1
                        continue
                    }
                }

                append(content[i])
                i++
            }
        }
    }

    Text(
        text = annotated,
        color = textColor,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 22.sp
    )
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBlockBg)
            .border(1.dp, NavyBorder, RoundedCornerShape(8.dp))
    ) {
        Column {
            // Header bar with language tag and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavySurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language.isNotBlank()) language.uppercase() else "CODE / TERMINAL",
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Hermes Code", code))
                            copied = true
                            Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copiar Código",
                        tint = if (copied) GoldPrimary else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copiado" else "Copiar",
                        color = if (copied) GoldPrimary else TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuilder = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = line.trim().removePrefix("```").trim()
            }
        } else if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
        } else {
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            } else if (trimmed.isNotBlank()) {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
    }

    return blocks
}
