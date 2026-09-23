package com.shoppingconnect.aistudio.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.formatDate
import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.ManualMetricDao
import com.shoppingconnect.aistudio.data.db.ManualMetricEntity
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.db.RenderState
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.ui.components.CenterTopBar
import com.shoppingconnect.aistudio.ui.components.KeyValue
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.statusColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(repo: ProjectRepository, gens: GenerationDao, renders: RenderJobDao, private val metrics: ManualMetricDao) : ViewModel() {
    val projects = repo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val generations = gens.observeRecent(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val renders = renders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manual = metrics.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(label: String, amount: Long, note: String) = viewModelScope.launch { metrics.upsert(ManualMetricEntity(newId(), null, label, amount, note, System.currentTimeMillis())) }
    fun delete(id: String) = viewModelScope.launch { metrics.delete(id) }
}

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel = hiltViewModel()) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val gens by vm.generations.collectAsStateWithLifecycle()
    val renders by vm.renders.collectAsStateWithLifecycle()
    val manual by vm.manual.collectAsStateWithLifecycle()
    Scaffold(topBar = { CenterTopBar("분석") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Column(Modifier.widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(title = "프로젝트 상태 (로컬 통계)") {
                        val total = projects.size.coerceAtLeast(1)
                        ProjectStatus.entries.forEach { s ->
                            val n = projects.count { it.status == s }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${s.label} $n 개" }) {
                                Text(s.label, Modifier.width(88.dp), style = MaterialTheme.typography.labelMedium)
                                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                    Box(Modifier.fillMaxWidth(n / total.toFloat()).fillMaxHeight().background(statusColor(s)))
                                }
                                Text("  $n", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    SectionCard(title = "AI 사용량") {
                        KeyValue("생성 실행", "${gens.size}회 (성공 ${gens.count { it.state.name in setOf("SUCCEEDED", "PARTIAL") }})")
                        KeyValue("입력 토큰", "%,d".format(gens.sumOf { it.inputTokens }))
                        KeyValue("출력 토큰", "%,d".format(gens.sumOf { it.outputTokens }))
                        Text("토큰 수는 Claude API 응답의 usage 값을 기록한 것입니다. 요금은 Anthropic 콘솔에서 확인하세요.", style = MaterialTheme.typography.labelSmall)
                    }
                    SectionCard(title = "렌더 통계") {
                        val ok = renders.filter { it.state == RenderState.SUCCEEDED }
                        KeyValue("완료", "${ok.size}건")
                        KeyValue("평균 렌더 시간", if (ok.isEmpty()) "-" else "%.1f초".format(ok.map { it.renderMs }.average() / 1000))
                        KeyValue("실패", "${renders.count { it.state == RenderState.FAILED }}건")
                    }
                    ManualMetrics(manual, vm)
                }
            }
        }
    }
}

@Composable
private fun ManualMetrics(list: List<ManualMetricEntity>, vm: AnalyticsViewModel) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    SectionCard(title = "수익·성과 (직접 입력)") {
        Text("이 앱은 판매액이나 수익을 확인할 공식 데이터 연결이 없어 자동으로 표시하지 않습니다. 직접 확인한 값만 기록하세요.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(label, { label = it }, Modifier.weight(1f), label = { Text("항목") }, singleLine = true)
            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), label = { Text("금액(원)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("메모") })
        Button(onClick = { vm.add(label.ifBlank { "수익" }, amount.toLongOrNull() ?: 0, note); label = ""; amount = ""; note = "" }, enabled = amount.isNotBlank()) { Text("기록") }
        if (list.isNotEmpty()) KeyValue("합계", formatPrice(list.sumOf { it.amount }) ?: "0원")
        list.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${m.date.formatDate()} · ${m.label} · ${formatPrice(m.amount) ?: "0원"}${if (m.note.isNotBlank()) " · ${m.note}" else ""}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { vm.delete(m.id) }) { Icon(Icons.Default.Delete, "삭제") }
            }
        }
    }
}
