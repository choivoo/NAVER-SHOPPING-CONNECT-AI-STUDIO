package com.shoppingconnect.aistudio.pipeline

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.db.RenderJobEntity
import com.shoppingconnect.aistudio.data.db.RenderState
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    private val wm: WorkManager,
    private val generations: GenerationDao,
    private val renders: RenderJobDao,
    private val projects: ProjectRepository,
    private val orchestrator: PipelineOrchestrator,
    private val settings: SettingsRepository,
    private val media: com.shoppingconnect.aistudio.data.repository.MediaRepository,
) {
    /** Demo Mode: sample product + app-drawn illustration, processed by the demo engine. */
    suspend fun startDemo(options: PipelineOptions): Started {
        val product = DemoData.product()
        val project = projects.createProject(product, settings.current().promptVersion)
        val bmp = DemoData.illustration()
        media.saveBitmap(project.id, bmp, com.shoppingconnect.aistudio.domain.model.AssetKind.ORIGINAL, "demo_illustration", copyright = com.shoppingconnect.aistudio.domain.model.CopyrightType.APP_GENERATED)
        bmp.recycle()
        return startGeneration(project.id, product.affiliateUrl, options, null, requireNetwork = false)
    }

    data class Started(val projectId: String, val generationId: String)

    /** Creates a project for [url] (product filled in by step 1) and starts the pipeline. */
    suspend fun startFromUrl(url: String, options: PipelineOptions, batchId: String? = null): Started {
        val placeholder = Product(id = newId(), sourceUrl = url, affiliateUrl = url, title = "")
        return start(placeholder, url, options, batchId)
    }

    /** Starts the pipeline with an already known product (manual input, search API, demo). */
    suspend fun startWithProduct(product: Product, options: PipelineOptions, batchId: String? = null): Started = start(product, product.affiliateUrl, options, batchId)

    private suspend fun start(product: Product, url: String, options: PipelineOptions, batchId: String?): Started {
        val project = projects.createProject(product, settings.current().promptVersion)
        return startGeneration(project.id, url, options, batchId)
    }

    /** Starts a new generation for an existing project (e.g. after manual product input). */
    suspend fun startGeneration(projectId: String, url: String, options: PipelineOptions, batchId: String?, requireNetwork: Boolean = true): Started {
        val gen = GenerationEntity(
            id = newId(), projectId = projectId, inputUrl = url, state = GenerationState.QUEUED,
            stepsJson = AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.shoppingconnect.aistudio.domain.model.StepStatus.serializer()), orchestrator.initialSteps()),
            optionsJson = AppJson.encodeToString(PipelineOptions.serializer(), options), model = "", promptVersion = settings.current().promptVersion,
            errorKind = null, errorMessage = null, warningsJson = "[]", batchId = batchId, workId = null, inputTokens = 0, outputTokens = 0,
            startedAt = System.currentTimeMillis(), finishedAt = null,
        )
        generations.upsert(gen)
        enqueue(gen.id, batchId != null, requireNetwork)
        return Started(projectId, gen.id)
    }

    /** Re-runs a failed/cancelled generation; completed steps are skipped. */
    suspend fun resume(genId: String) {
        val g = generations.get(genId) ?: return
        generations.upsert(g.copy(state = GenerationState.QUEUED, errorKind = null, errorMessage = null))
        enqueue(genId, false, projects.product(g.projectId)?.isDemo != true)
    }

    private suspend fun enqueue(genId: String, batch: Boolean, requireNetwork: Boolean = true) {
        val req = OneTimeWorkRequestBuilder<PipelineWorker>()
            .setInputData(workDataOf(PipelineWorker.KEY_GEN to genId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (requireNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_PIPELINE).addTag("gen:$genId")
            .build()
        generations.get(genId)?.let { generations.upsert(it.copy(workId = req.id.toString())) }
        if (batch) wm.enqueueUniqueWork(BATCH_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        else wm.enqueueUniqueWork("pipeline-$genId", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelGeneration(workId: String?) { workId?.let { runCatching { wm.cancelWorkById(UUID.fromString(it)) } } }

    /** Render Queue: jobs run one after another (APPEND). */
    suspend fun enqueueRender(projectId: String, shortformId: String, settings: RenderSettings): String {
        val job = RenderJobEntity(newId(), projectId, shortformId, RenderState.QUEUED, 0, null, null, AppJson.encodeToString(RenderSettings.serializer(), settings), null, 0, 0, System.currentTimeMillis(), null)
        val req = OneTimeWorkRequestBuilder<RenderWorker>().setInputData(workDataOf(RenderWorker.KEY_JOB to job.id)).addTag(TAG_RENDER).build()
        renders.upsert(job.copy(workId = req.id.toString()))
        wm.enqueueUniqueWork(RENDER_QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        return job.id
    }

    suspend fun cancelRender(jobId: String) {
        val j = renders.get(jobId) ?: return
        j.workId?.let { runCatching { wm.cancelWorkById(UUID.fromString(it)) } }
        renders.upsert(j.copy(state = RenderState.CANCELLED, finishedAt = System.currentTimeMillis()))
    }

    companion object {
        const val TAG_PIPELINE = "pipeline"
        const val TAG_RENDER = "render"
        const val BATCH_QUEUE = "batch-queue"
        const val RENDER_QUEUE = "render-queue"
    }
}
