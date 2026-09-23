package com.shoppingconnect.aistudio.ui.screens.projects

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.formatDateTime
import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.ProjectBundle
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.ConfirmDialog
import com.shoppingconnect.aistudio.ui.components.DemoBanner
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.KeyValue
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import com.shoppingconnect.aistudio.ui.components.StatusChip
import com.shoppingconnect.aistudio.ui.components.statusColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    generations: GenerationDao,
    renders: RenderJobDao,
    private val files: ProjectFiles,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    val bundle = repo.observeBundle(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val gens = generations.observeForProject(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val renders = renders.observeForProject(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatus(s: ProjectStatus) = viewModelScope.launch { repo.setStatus(projectId, s) }
    fun saveNotes(n: String) = viewModelScope.launch { repo.updateProject(projectId) { it.copy(analyticsNotes = n) } }
    fun delete(done: () -> Unit) = viewModelScope.launch { repo.deleteProject(projectId); done() }

    /** Project JSON backup (text data + asset metadata), shared through the Sharesheet. */
    suspend fun exportJson(): File {
        val json = repo.exportBackup(projectId)
        val f = File(files.shareDir, "project_${projectId.take(8)}.json")
        f.writeText(json)
        return f
    }
}

@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit, onEditor: (String) -> Unit, onPreview: (String) -> Unit, onPublish: (String) -> Unit,
    onVisuals: (String) -> Unit, onStudio: (String) -> Unit, onPipeline: (String) -> Unit, vm: ProjectDetailViewModel = hiltViewModel(),
) {
    val b by vm.bundle.collectAsStateWithLifecycle()
    val gens by vm.gens.collectAsStateWithLifecycle()
    val renders by vm.renders.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundle = b
    Scaffold(topBar = {
        AppTopBar(bundle?.project?.title ?: "프로젝트", onBack) {
            IconButton(onClick = {
                scope.launch {
                    val f = vm.exportJson()
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "프로젝트 백업 내보내기"))
                }
            }) { Icon(Icons.Default.SaveAlt, "프로젝트 JSON 백업") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "프로젝트 삭제") }
        }
    }) { pad ->
        if (bundle == null) { Column(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                listOf("개요", "상품 사실", "블로그", "비주얼", "숏폼", "기록", "메모").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Column(Modifier.widthIn(max = 840.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (bundle.project.isDemo) DemoBanner()
                        when (tab) {
                            0 -> Overview(bundle, gens.firstOrNull()?.id, onPipeline, { statusMenu = true }, statusMenu, { statusMenu = false }, vm::setStatus, onEditor, onPreview, onPublish, onVisuals, onStudio)
                            1 -> Facts(bundle)
                            2 -> BlogTab(bundle, onEditor, onPreview, onPublish)
                            3 -> VisualTab(bundle, onVisuals)
                            4 -> ShortsTab(bundle, renders.map { "${it.state.name} ${it.progress}% · ${it.createdAt.formatDateTime()}" + (it.errorMessage?.let { e -> " · $e" } ?: "") }, onStudio)
                            5 -> History(gens.map { "${it.startedAt.formatDateTime()} · ${it.state.name} · ${it.model.ifBlank { "-" }} · 토큰 ${it.inputTokens}/${it.outputTokens}" + (it.errorMessage?.let { e -> "\n$e" } ?: "") })
                            else -> Notes(bundle.project.analyticsNotes, vm::saveNotes)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("프로젝트 삭제", "이 프로젝트와 로컬에 저장된 이미지·영상이 모두 삭제됩니다.", "삭제", destructive = true, onConfirm = { vm.delete(onBack) }, onDismiss = { confirmDelete = false })
}

@Composable
private fun Overview(
    b: ProjectBundle, genId: String?, onPipeline: (String) -> Unit, openStatus: () -> Unit, statusMenu: Boolean, closeStatus: () -> Unit,
    setStatus: (ProjectStatus) -> Unit, onEditor: (String) -> Unit, onPreview: (String) -> Unit, onPublish: (String) -> Unit, onVisuals: (String) -> Unit, onStudio: (String) -> Unit,
) {
    val id = b.project.id
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileImage(b.project.thumbnailPath ?: b.assets.firstOrNull { it.kind == AssetKind.ORIGINAL }?.path, Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(b.project.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(b.project.status.label, statusColor(b.project.status))
                    Column {
                        TextButton(onClick = openStatus) { Text("상태 변경") }
                        DropdownMenu(statusMenu, closeStatus) { ProjectStatus.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { setStatus(s); closeStatus() }) } }
                    }
                }
                Text("생성 ${b.project.createdAt.formatDateTime()} · 수정 ${b.project.updatedAt.formatDateTime()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEditor(id) }) { Icon(Icons.AutoMirrored.Filled.Article, null); Spacer(Modifier.width(6.dp)); Text("블로그 편집") }
        FilledTonalButton(onClick = { onPreview(id) }) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(6.dp)); Text("미리보기") }
        FilledTonalButton(onClick = { onVisuals(id) }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("이미지 카드") }
        FilledTonalButton(onClick = { onStudio(id) }) { Icon(Icons.Default.Movie, null); Spacer(Modifier.width(6.dp)); Text("Shorts Studio") }
        OutlinedButton(onClick = { onPublish(id) }) { Icon(Icons.Default.Publish, null); Spacer(Modifier.width(6.dp)); Text("게시 준비") }
        if (genId != null) TextButton(onClick = { onPipeline(genId) }) { Text("파이프라인 기록") }
    }
    b.intelligence?.let { intel ->
        SectionCard(title = "Product Intelligence Report") {
            if (intel.summary.isNotBlank()) Text(intel.summary)
            Bullet("핵심 특징", intel.keyFeatures); Bullet("대상 소비자", intel.targetAudience); Bullet("사용 상황", intel.useCases)
            Bullet("장점", intel.pros); Bullet("주의점", intel.cautions); Bullet("비교 포인트", intel.comparisonPoints)
            if (intel.searchIntent.isNotBlank()) KeyValue("검색 의도", intel.searchIntent)
            Bullet("주요 키워드", intel.keywords); Bullet("롱테일 키워드", intel.longTailKeywords); Bullet("제목 아이디어", intel.titleIdeas); Bullet("콘텐츠 Angle", intel.contentAngles)
        }
    }
}

@Composable
fun Bullet(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
}

/** Product Facts Panel + Citation/Source Panel: verified source data vs. unknown fields. */
@Composable
fun Facts(b: ProjectBundle) {
    val p: Product = b.product ?: run { Text("상품 정보가 아직 없습니다."); return }
    SectionCard(title = "검증된 상품 사실 (SOURCE DATA)") {
        KeyValue("상품명", p.title, !p.isVerified(Product.F_TITLE))
        KeyValue("브랜드", p.brand, !p.isVerified(Product.F_BRAND))
        KeyValue("카테고리", p.category, !p.isVerified(Product.F_CATEGORY))
        KeyValue("판매가", formatPrice(p.price, p.currency), !p.isVerified(Product.F_PRICE))
        KeyValue("정가", formatPrice(p.originalPrice, p.currency), !p.isVerified(Product.F_ORIGINAL_PRICE))
        p.discountRate?.let { KeyValue("할인율(계산)", "$it%") }
        KeyValue("판매처", p.seller, !p.isVerified(Product.F_SELLER))
        p.rating?.let { KeyValue("평점", it.toString()) }
        p.reviewCount?.let { KeyValue("리뷰 수", it.toString()) }
        p.specifications.forEach { KeyValue(it.name, it.value) }
        if (p.uncertainFields.isNotEmpty()) Text("확인 불가(unknown): ${p.uncertainFields.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
    SectionCard(title = "출처 (Citation / Source)") {
        KeyValue("출처", p.source.label)
        KeyValue("원본 URL", p.sourceUrl)
        KeyValue("제휴 링크", p.affiliateUrl)
        KeyValue("정보 확인 시각", p.extractionTimestamp.formatDateTime())
        if (p.redirectChain.isNotEmpty()) KeyValue("리다이렉트", p.redirectChain.joinToString(" → "))
    }
    SectionCard(title = "이미지 출처") {
        b.assets.filter { it.mimeType.startsWith("image/") }.forEach { a ->
            Text("• ${a.kind.label} · ${a.copyrightType.label}${if (a.aiGenerated) " · 생성 그래픽" else ""} · ${a.width}×${a.height}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BlogTab(b: ProjectBundle, onEditor: (String) -> Unit, onPreview: (String) -> Unit, onPublish: (String) -> Unit) {
    val a = b.article
    SectionCard(title = "블로그") {
        if (a == null) Text("아직 생성된 글이 없습니다.") else {
            Text(a.title, style = MaterialTheme.typography.titleMedium)
            Text("${a.charCount}자 · 블록 ${a.blocks.size}개 · 해시태그 ${a.hashtags.size}개 · Content Quality Score ${b.qualityScore}", style = MaterialTheme.typography.bodySmall)
            Text("모델 ${b.model ?: "-"} · 프롬프트 ${b.promptVersion ?: "-"}", style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEditor(b.project.id) }) { Text("편집") }
            OutlinedButton(onClick = { onPreview(b.project.id) }) { Text("미리보기") }
            OutlinedButton(onClick = { onPublish(b.project.id) }) { Text("게시 준비") }
        }
    }
}

@Composable
private fun VisualTab(b: ProjectBundle, onVisuals: (String) -> Unit) {
    val cards = b.visualPlan?.cards.orEmpty()
    SectionCard(title = "이미지 카드 ${cards.size}장") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards) { c -> FileImage(b.assets.firstOrNull { it.id == c.renderedAssetId }?.path, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)), crop = false) }
        }
        Button(onClick = { onVisuals(b.project.id) }) { Text("카드 편집") }
    }
    SectionCard(title = "에셋 라이브러리") {
        AssetKind.entries.forEach { k ->
            val n = b.assets.count { it.kind == k }
            if (n > 0) Text("${k.label}: ${n}개", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShortsTab(b: ProjectBundle, renders: List<String>, onStudio: (String) -> Unit) {
    SectionCard(title = "숏폼") {
        val t = b.timeline
        if (t == null) Text("아직 숏폼 드래프트가 없습니다.") else {
            Text("${t.template.label} · 장면 ${t.scenes.size}개 · ${t.durationMs / 1000}초 · ${t.render.resolution.label}")
            b.videoPath?.let { Text("렌더된 영상: ${File(it).name}", style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = { onStudio(b.project.id) }) { Text("Shorts Studio 열기") }
    }
    if (renders.isNotEmpty()) SectionCard(title = "렌더 기록") { renders.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun History(items: List<String>) {
    SectionCard(title = "생성 기록") { if (items.isEmpty()) Text("기록 없음") else items.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun Notes(initial: String, save: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    SectionCard(title = "Analytics Notes") {
        Text("게시 후 성과 등 직접 확인한 내용을 기록하세요. 앱은 성과 데이터를 임의로 만들지 않습니다.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 5)
        Button(onClick = { save(text) }) { Text("저장") }
    }
}
