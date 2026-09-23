package com.shoppingconnect.aistudio.pipeline

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shoppingconnect.aistudio.AiStudioApp
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.db.RenderState
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.media.video.ShortsRenderer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

private fun foregroundInfo(id: Int, notification: android.app.Notification) =
    if (Build.VERSION.SDK_INT >= 35) ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
    else ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

@HiltWorker
class PipelineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: PipelineOrchestrator,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(NOTIF_ID, notifier.progress(AiStudioApp.CHANNEL_AI, "AI 콘텐츠 생성 중", "준비 중…", 0, null, null))

    override suspend fun doWork(): Result {
        val genId = inputData.getString(KEY_GEN) ?: return Result.failure()
        runCatching { setForeground(getForegroundInfo()) }.onFailure { AppLog.w("PipelineWorker", "foreground not allowed; continuing in background", it) }
        orchestrator.run(genId) { step ->
            setProgress(workDataOf(KEY_STEP to step.index))
            runCatching {
                setForeground(foregroundInfo(NOTIF_ID, notifier.progress(AiStudioApp.CHANNEL_AI, "AI 콘텐츠 생성 중", "${step.index}/8 ${step.label}", step.index * 100 / 8, WorkManager.getInstance(applicationContext).createCancelPendingIntent(id), null)))
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_GEN = "generation_id"
        const val KEY_STEP = "step"
        const val NOTIF_ID = 4101
    }
}

@HiltWorker
class RenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val projects: ProjectRepository,
    private val renders: RenderJobDao,
    private val files: ProjectFiles,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(NOTIF_ID, notifier.progress(AiStudioApp.CHANNEL_RENDER, "Shorts Rendering", "대기 중", 0, null, null))

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB) ?: return Result.failure()
        val job = renders.get(jobId) ?: return Result.failure()
        if (job.state == RenderState.CANCELLED) return Result.success()
        val (shortformId, timeline) = projects.timeline(job.projectId) ?: return fail(jobId, "숏폼이 없습니다.")
        val settings = runCatching { AppJson.decodeFromString(RenderSettings.serializer(), job.settingsJson) }.getOrDefault(timeline.render)
        val out = File(files.dir(job.projectId, com.shoppingconnect.aistudio.domain.model.AssetKind.VIDEO), "shorts_${jobId.take(8)}.mp4")
        renders.upsert(job.copy(state = RenderState.RENDERING, progress = 0, workId = id.toString()))
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        runCatching { setForeground(foregroundInfo(NOTIF_ID, notifier.progress(AiStudioApp.CHANNEL_RENDER, "Shorts Rendering", "0%", 0, cancelIntent, job.projectId))) }
        var lastPct = -1
        return try {
            val stats = withContext(Dispatchers.Default) {
                runInterruptible {
                    ShortsRenderer(timeline.copy(render = settings)) { s -> s.mediaPath?.takeIf { files.isInside(it) } }
                        .render(out, onProgress = { pct ->
                            if (pct != lastPct) {
                                lastPct = pct
                                kotlinx.coroutines.runBlocking {
                                    renders.progress(jobId, pct, RenderState.RENDERING)
                                    setProgress(workDataOf(KEY_PROGRESS to pct))
                                    runCatching { setForeground(foregroundInfo(NOTIF_ID, notifier.progress(AiStudioApp.CHANNEL_RENDER, "Shorts Rendering", "$pct%", pct, cancelIntent, job.projectId))) }
                                }
                            }
                        }, isCancelled = { isStopped })
                }
            }
            projects.setVideoOutput(shortformId, out.absolutePath)
            renders.get(jobId)?.let { renders.upsert(it.copy(state = RenderState.SUCCEEDED, progress = 100, outputPath = out.absolutePath, renderMs = stats.renderMs, frames = stats.frames, finishedAt = System.currentTimeMillis())) }
            notifier.done(job.projectId, "숏폼 영상 렌더링이 완료되었습니다.", "${stats.width}x${stats.height} · ${stats.frames}프레임")
            Result.success(workDataOf(KEY_OUTPUT to out.absolutePath))
        } catch (e: Throwable) {
            val ex = e.toAppException()
            val cancelled = isStopped || ex.kind == com.shoppingconnect.aistudio.core.common.ErrorKind.Cancelled
            renders.get(jobId)?.let { renders.upsert(it.copy(state = if (cancelled) RenderState.CANCELLED else RenderState.FAILED, errorMessage = ex.userMessage, finishedAt = System.currentTimeMillis())) }
            if (cancelled) Result.success() else fail(jobId, ex.userMessage)
        }
    }

    private suspend fun fail(jobId: String, msg: String): Result {
        renders.get(jobId)?.let { renders.upsert(it.copy(state = RenderState.FAILED, errorMessage = msg, finishedAt = System.currentTimeMillis())) }
        return Result.failure(workDataOf("error" to msg))
    }

    companion object {
        const val KEY_JOB = "render_job_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT = "output"
        const val NOTIF_ID = 4102
    }
}
