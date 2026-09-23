package com.shoppingconnect.aistudio.ui.screens.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.components.CenterTopBar
import com.shoppingconnect.aistudio.ui.components.EmptyState
import com.shoppingconnect.aistudio.ui.components.ProjectCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(repo: ProjectRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val status = MutableStateFlow<ProjectStatus?>(null)
    val projects = combine(query, status) { q, s -> q to s }
        .flatMapLatest { (q, s) -> repo.search(q, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun ProjectsScreen(onOpen: (String) -> Unit, onBatch: () -> Unit, vm: ProjectsViewModel = hiltViewModel()) {
    val list by vm.projects.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val st by vm.status.collectAsStateWithLifecycle()
    val wl = LocalWindowLayout.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = { CenterTopBar("프로젝트") { IconButton(onClick = onBatch) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Batch Workspace") } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { vm.query.value = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("상품명·브랜드·태그·상태 검색") })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(st == null, { vm.status.value = null }, { Text("전체") }) }
                items(ProjectStatus.entries) { s -> FilterChip(st == s, { vm.status.value = s }, { Text(s.label) }) }
            }
            if (list.isEmpty()) EmptyState(Icons.Default.Folder, if (q.isBlank() && st == null) "첫 쇼핑 콘텐츠를 만들어보세요." else "검색 결과가 없습니다.")
            else Box(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { p -> ProjectCard(p, onClick = { selected = p.id; onOpen(p.id) }, selected = !wl.isCompact && selected == p.id) }
                }
            }
        }
    }
}
