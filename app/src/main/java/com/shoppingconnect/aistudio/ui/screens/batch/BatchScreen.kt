package com.shoppingconnect.aistudio.ui.screens.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.ProjectDao
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.GenerationState
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.pipeline.WorkScheduler
import com.shoppingconnect.aistudio.product.LinkCheck
import com.shoppingconnect.aistudio.product.UrlProcessor
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.StatusChip
import com.shoppingconnect.aistudio.ui.theme.LocalExtraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatchRow(val genId: String, val projectId: String, val title: String, val state: GenerationState)

/** Batch Workspace: queues several links; each project still requires individual review before posting. */
@HiltViewModel
class BatchViewModel @Inject constructor(private val scheduler: WorkScheduler, generations: GenerationDao, projects: ProjectDao) : ViewModel() {
    val rows = combine(generations.observeBatches(), projects.observeAll()) { gens, ps ->
        gens.map { g -> BatchRow(g.id, g.projectId, ps.firstOrNull { it.id == g.projectId }?.title?.ifBlank { g.inputUrl } ?: g.inputUrl, g.state) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enqueue(text: String, usage: UsageStatus, onResult: (Int, Int) -> Unit) = viewModelScope.launch {
        val links = text.lines().mapNotNull { UrlProcessor.extractUrl(it.trim()) }.distinct().take(20)
        val ok = links.filter { UrlProcessor.check(it) is LinkCheck.Compatible }
        val batchId = newId()
        ok.forEach { scheduler.startFromUrl(it, PipelineOptions(strategy = ContentStrategy(usage = usage)), batchId) }
        onResult(ok.size, links.size - ok.size)
    }
}

@Composable
fun BatchScreen(onBack: () -> Unit, onOpenProject: (String) -> Unit, vm: BatchViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val extra = LocalExtraColors.current
    Scaffold(topBar = { AppTopBar("Batch Workspace", onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard(title = "여러 링크 추가") {
                    Text("한 줄에 링크 하나씩 (최대 20개). 순서대로 생성되며, 각 프로젝트는 게시 전 개별 검수가 필요합니다. 자동 대량 게시는 지원하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("링크 목록") })
                    Button(onClick = { vm.enqueue(text, UsageStatus.INTRO_ONLY) { ok, bad -> msg = "대기열에 ${ok}개 추가" + if (bad > 0) " · 지원하지 않는 링크 ${bad}개 제외" else ""; text = "" } }, enabled = text.isNotBlank()) { Text("Queue에 추가 (단순 소개 콘텐츠)") }
                    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Text("Queue", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(rows, key = { _, r -> r.genId }) { i, r ->
                Card(onClick = { onOpenProject(r.projectId) }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
                        Text(r.title, Modifier.weight(1f), maxLines = 2)
                        val (label, color) = when (r.state) {
                            GenerationState.QUEUED -> "Ready" to MaterialTheme.colorScheme.outline
                            GenerationState.RUNNING -> "Generating" to MaterialTheme.colorScheme.primary
                            GenerationState.SUCCEEDED, GenerationState.PARTIAL -> "Complete" to extra.success
                            GenerationState.FAILED -> "Failed" to MaterialTheme.colorScheme.error
                            GenerationState.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.outline
                        }
                        StatusChip(label, color)
                    }
                }
            }
        }
    }
}
