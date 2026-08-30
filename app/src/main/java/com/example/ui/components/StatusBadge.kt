package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ServerHealth
import com.example.ui.theme.*

@Composable
fun ServerStatusBadge(
    health: ServerHealth?,
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = health?.isReachable == true
    val isChecking = health == null

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val statusColor = when {
        isChecking -> StatusWarning
        isOnline -> StatusOnline
        else -> StatusOffline
    }

    val statusText = when {
        isChecking -> "A verificar..."
        isOnline -> "${health.latencyMs} ms"
        else -> "Termux Off"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NavySurfaceCard)
            .border(1.dp, if (isOnline) GoldPrimary.copy(alpha = 0.25f) else NavyBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(10.dp)
        ) {
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(pulseScale)
                        .background(StatusOnline.copy(alpha = 0.35f), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = statusText,
            color = if (isOnline) StatusOnline else StatusOffline,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
