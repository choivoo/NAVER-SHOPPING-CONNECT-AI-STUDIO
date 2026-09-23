package com.shoppingconnect.aistudio.ui.screens.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.formatDateTime
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.db.RenderState
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.ui.components.CenterTopBar
import com.shoppingconnect.aistudio.ui.components.EmptyState
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ContentRow(val projectId: String, val title: String, val updatedAt: Long, val score: Int, val thumb: String?, val extra: String)

@HiltViewModel
class ListsViewModel @Inject constructor(repo: ProjectRepository, renders: RenderJobDao) : ViewModel() {
    val articles = combine(repo.observeArticles(), repo.observeProjects()) { a, p ->
        a.map { e -> ContentRow(e.projectId, e.title.ifBlank { "제목 없음" }, e.updatedAt, e.qualityScore, p.firstOrNull { it.id == e.projectId }?.thumbnailPath, p.firstOrNull { it.id == e.projectId }?.status?.label.orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shorts = combine(repo.observeShortforms(), repo.observeProjects()) { s, p ->
        s.map { e -> ContentRow(e.projectId, p.firstOrNull { it.id == e.projectId }?.title ?: "숏폼", e.updatedAt, 0, e.thumbnailPath, "${e.durationMs / 1000}초" + if (e.outputPath != null) " · 렌더 완료" else " · 드래프트") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val queue = renders.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
private fun ContentRowCard(r: ContentRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileImage(r.thumb, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text("${r.extra} · ${r.updatedAt.formatDateTime()}" + if (r.score > 0) " · Quality ${r.score}" else "", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ContentScreen(onOpen: (String) -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val list by vm.articles.collectAsStateWithLifecycle()
    Scaffold(topBar = { CenterTopBar("콘텐츠") }) { pad ->
        if (list.isEmpty()) EmptyState(Icons.AutoMirrored.Filled.Article, "작성된 블로그 글이 없습니다.", "홈에서 링크를 붙여넣어 시작하세요.", modifier = Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.projectId }) { ContentRowCard(it) { onOpen(it.projectId) } }
        }
    }
}

@Composable
fun ShortsListScreen(onOpen: (String) -> Unit, vm: ListsViewModel = hiltViewModel()) {
    val list by vm.shorts.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    Scaffold(topBar = { CenterTopBar("Shorts") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val active = queue.filter { it.state == RenderState.QUEUED || it.state == RenderState.RENDERING }
            if (active.isNotEmpty()) item {
                SectionCard(title = "Render Queue") {
                    active.forEach { r ->
                        Text(if (r.state == RenderState.RENDERING) "현재: Rendering ${r.progress}%" else "다음: Waiting", style = MaterialTheme.typography.bodyMedium)
                        if (r.state == RenderState.RENDERING) LinearProgressIndicator(progress = { r.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Default.Movie, "숏폼이 없습니다.", "블로그 생성 시 숏폼 드래프트가 함께 만들어집니다.") }
            items(list, key = { it.projectId }) { ContentRowCard(it) { onOpen(it.projectId) } }
        }
    }
}
