package com.shoppingconnect.aistudio.pipeline

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shoppingconnect.aistudio.AiStudioApp
import com.shoppingconnect.aistudio.MainActivity
import com.shoppingconnect.aistudio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Notifications are only posted when the user granted permission (Android 13+). */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun allowed(): Boolean {
        val runtimeGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openIntent(projectId: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            projectId?.let { putExtra(MainActivity.EXTRA_PROJECT_ID, it) }
        }
        return PendingIntent.getActivity(context, projectId.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun done(projectId: String?, title: String, text: String) {
        if (!allowed()) return
        val n = NotificationCompat.Builder(context, AiStudioApp.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(openIntent(projectId)).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify((projectId ?: title).hashCode(), n) } catch (_: SecurityException) { }
    }

    fun progress(channel: String, title: String, text: String, progress: Int, cancel: PendingIntent?, projectId: String?) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .setContentIntent(openIntent(projectId))
            .apply { cancel?.let { addAction(0, "취소", it) } }
            .build()
}
