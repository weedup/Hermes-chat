package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.HermesApiClient
import com.example.data.PreferencesManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NotificationPollWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val apiClient = HermesApiClient()
    private val preferencesManager = PreferencesManager(context)
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        return try {
            val settings = preferencesManager.settingsFlow.first()
            val serverUrl = settings.serverUrl
            val pending = apiClient.fetchPendingNotifications(serverUrl)
            if (!pending.isNullOrEmpty()) {
                pending.forEach { n ->
                    notificationHelper.show(n.tag, n.title.ifBlank { "Hermes" }, n.body)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "HermesNotificationPollWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
