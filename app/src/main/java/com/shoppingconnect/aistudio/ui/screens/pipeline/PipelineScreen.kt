package com.shoppingconnect.aistudio.ui.screens.pipeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.ErrorKind
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.GenerationEntity
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineMode
import com.shoppingconnect.aistudio.domain.model.PipelineStep
import com.shoppingconnect.aistudio.domain.model.StepState
import com.shoppingconnect.aistudio.domain.model.StepStatus
import com.shoppingconnect.aistudio.data.db.ShortformDao
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.pipeline.PipelineOrchestrator
import com.shoppingconnect.aistudio.pipeline.WorkScheduler
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import com.shoppingconnect.aistudio.ui.theme.LocalExtraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PipelineViewModel @Inject constructor(
    handle: SavedStateHandle,
    generations: GenerationDao,
    val orchestrator: PipelineOrchestrator,
    private val scheduler: WorkScheduler,
    private val shortforms: ShortformDao,
    private val settings: SettingsRepository,
) : ViewModel() {
    val genId: String = checkNotNull(handle["genId"])
    val gen: StateFlow<GenerationEntity?> = generations.observe(genId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private var magicRenderQueued = false

    fun retry() = viewModelScope.launch { scheduler.resume(genId) }
    fun cancel() { scheduler.cancelGeneration(gen.value?.workId) }

    /** ⚡ Magic mode: once the draft is ready the 1080p render is queued automatically. */
    fun onSucceeded(g: GenerationEntity) {
        if (magicRenderQueued || orchestrator.decodeOptions(g).mode != PipelineMode.MAGIC) return
        magicRenderQueued = true
        viewModelScope.launch {
            val sf = shortforms.forProject(g.projectId) ?: return@launch
            if (sf.outputPath != null) return@launch
            val tl = com.shoppingconnect.aistudio.core.json.AppJson.decodeFromString(com.shoppingconnect.aistudio.domain.model.ShortTimeline.serializer(), sf.timelineJson)
            scheduler.enqueueRender(g.projectId, sf.id, tl.render)
            settings.current()
        }
    }
}

@Composable
fun PipelineScreen(
    onBack: () -> Unit, onOpenProject: (String) -> Unit, onOpenEditor: (String) -> Unit, onOpenStudio: (String) -> Unit,
    onManual: (url: String, genId: String) -> Unit, onSettings: () -> Unit, vm: PipelineViewModel = hiltViewModel(),
) {
    val g by vm.gen.collectAsStateWithLifecycle()
    val gen = g
    Scaffold(topBar = { AppTopBar("AI 파이프라인", onBack) }) { pad ->
        if (gen == null) { Column(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        val steps = vm.orchestrator.decodeSteps(gen)
        val warnings = vm.orchestrator.decodeWarnings(gen)
        val done = steps.count { it.state in setOf(StepState.COMPLETE, StepState.WARNING, StepState.SKIPPED) }
        val running = gen.state == GenerationState.RUNNING || gen.state == GenerationState.QUEUED
        if (gen.state == GenerationState.SUCCEEDED || gen.state == GenerationState.PARTIAL) vm.onSucceeded(gen)
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running) SparkIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("$done/${PipelineStep.TOTAL} " + (steps.firstOrNull { it.state == StepState.RUNNING }?.step?.label ?: if (running) "대기 중" else gen.state.name),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (running) TextButton(onClick = vm::cancel) { Text("취소") }
                    }
                    LinearProgressIndicator(progress = { done / PipelineStep.TOTAL.toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (gen.model.isNotBlank()) Text("모델: ${gen.model} · 프롬프트 ${gen.promptVersion}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(steps, key = { it.step.name }) { s -> StepCard(s, Modifier.widthIn(max = 720.dp)) }
            if (gen.state == GenerationState.FAILED) item {
                Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(gen.errorMessage ?: "오류가 발생했습니다.", color = MaterialTheme.colorScheme.error)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::retry) { Text("다시 시도") }
                        if (gen.errorKind in listOf(ErrorKind.ProductExtractionFailed.name, ErrorKind.InvalidUrl.name, ErrorKind.UnsupportedFormat.name)) OutlinedButton(onClick = { onManual(gen.inputUrl, gen.id) }) { Text("직접 입력") }
                        if (gen.errorKind in listOf(ErrorKind.AiNotConfigured.name, ErrorKind.AiUnauthorized.name, ErrorKind.AiUnavailable.name)) OutlinedButton(onClick = onSettings) { Text("AI 설정") }
                        TextButton(onClick = { onOpenProject(gen.projectId) }) { Text("프로젝트 열기") }
                    }
                }
            }
            if (warnings.isNotEmpty()) item {
                SectionCard(Modifier.widthIn(max = 720.dp), title = "확인이 필요한 항목 ${warnings.size}") {
                    warnings.forEach { w -> Row { Icon(Icons.Default.Warning, null, tint = LocalExtraColors.current.warning, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(w, style = MaterialTheme.typography.bodySmall) } }
                }
            }
            if (gen.state == GenerationState.SUCCEEDED || gen.state == GenerationState.PARTIAL) item {
                Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenEditor(gen.projectId) }, Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Filled.Article, null); Spacer(Modifier.width(6.dp)); Text("블로그 글 검수하기") }
                    OutlinedButton(onClick = { onOpenStudio(gen.projectId) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Movie, null); Spacer(Modifier.width(6.dp)); Text("숏폼 확인하기") }
                    TextButton(onClick = { onOpenProject(gen.projectId) }, Modifier.fillMaxWidth()) { Text("프로젝트 카드 열기") }
                }
            }
        }
    }
}

@Composable
private fun StepCard(s: StepStatus, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    val (label, color) = when (s.state) {
        StepState.WAITING -> "Waiting" to MaterialTheme.colorScheme.outline
        StepState.RUNNING -> "Running" to MaterialTheme.colorScheme.primary
        StepState.COMPLETE -> "Complete" to extra.success
        StepState.WARNING -> "Warning" to extra.warning
        StepState.ERROR -> "Error" to MaterialTheme.colorScheme.error
        StepState.SKIPPED -> "Skipped" to MaterialTheme.colorScheme.outline
    }
    Card(
        modifier.fillMaxWidth().animateContentSize().semantics(mergeDescendants = true) { contentDescription = "${s.step.index}단계 ${s.step.label} $label ${s.message}" },
        colors = CardDefaults.cardColors(containerColor = if (s.state == StepState.RUNNING) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when (s.state) {
                StepState.RUNNING -> SparkIndicator()
                StepState.COMPLETE -> Icon(Icons.Default.CheckCircle, null, tint = color)
                StepState.WARNING -> Icon(Icons.Default.Warning, null, tint = color)
                StepState.ERROR -> Icon(Icons.Default.ErrorOutline, null, tint = color)
                StepState.SKIPPED -> Icon(Icons.Default.SkipNext, null, tint = color)
                StepState.WAITING -> Icon(Icons.Default.HourglassEmpty, null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${s.step.index}/${PipelineStep.TOTAL} ${s.step.label}", style = MaterialTheme.typography.titleSmall)
                if (s.message.isNotBlank()) Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
