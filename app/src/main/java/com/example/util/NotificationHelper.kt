package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

/**
 * Notificações locais alimentadas pela bridge (porta 9120).
 * Os cron jobs do Hermes fazem POST /notify; a app faz polling e mostra aqui.
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "hermes_notify"
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hermes (crons e bridge)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações enviadas pelos cron jobs do Hermes via bridge 9120"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun show(tag: String, title: String, body: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val intent: Intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tag.hashCode(), notif)
        } catch (_: SecurityException) {
            // Permissão revogada a meio — ignora
        }
    }
}
