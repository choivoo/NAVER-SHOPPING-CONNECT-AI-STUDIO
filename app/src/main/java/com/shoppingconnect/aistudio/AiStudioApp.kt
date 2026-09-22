package com.shoppingconnect.aistudio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.shoppingconnect.aistudio.core.common.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AiStudioApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        createChannels()
        AppLog.i("App", "started v${BuildConfig.VERSION_NAME}")
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RENDER, getString(R.string.notif_channel_render), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_AI, getString(R.string.notif_channel_ai), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_DONE, getString(R.string.notif_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()

    companion object {
        const val CHANNEL_RENDER = "render"
        const val CHANNEL_AI = "ai"
        const val CHANNEL_DONE = "done"
    }
}
