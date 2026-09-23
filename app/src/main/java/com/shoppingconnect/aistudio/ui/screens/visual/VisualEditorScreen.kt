package com.shoppingconnect.aistudio.ui.screens.visual

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.AspectRatio
import com.shoppingconnect.aistudio.domain.model.BlogCard
import com.shoppingconnect.aistudio.domain.model.CardStyle
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ExtractedPalette
import com.shoppingconnect.aistudio.domain.model.TextAlign
import com.shoppingconnect.aistudio.media.shorts.History
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.WorkspacePanes
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.LabeledSlider
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import com.shoppingconnect.aistudio.visual.CardRenderer
import com.shoppingconnect.aistudio.visual.VisualService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VisualEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val media: MediaRepository,
    private val visuals: VisualService,
    private val settings: SettingsRepository,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    private val cardId: String = checkNotNull(handle["cardId"])
    val assets = media.observe(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _card = MutableStateFlow<BlogCard?>(null)
    val card = _card.asStateFlow()
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview = _preview.asStateFlow()
    private val _undo = MutableStateFlow(false to false)
    val undoState = _undo.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private lateinit var history: History<BlogCard>
    private var palette: ExtractedPalette? = null
    private var renderJob: Job? = null
    private val store = CardStore(repo, visuals, media)

    init {
        viewModelScope.launch {
            val b = repo.observeBundleOnce(projectId)
            val c = b?.visualPlan?.cards?.firstOrNull { it.id == cardId } ?: return@launch
            history = History(c)
            palette = visuals.palette(c.imageAssetId)
            _card.value = c
            rerender()
        }
    }

    fun edit(push: Boolean = true, f: (BlogCard) -> BlogCard) {
        val c = _card.value ?: return
        val n = f(c)
        if (push) history.push(n) else history.replace(n)
        _card.value = n; _undo.value = history.canUndo to history.canRedo
        rerender()
    }
    fun style(f: (CardStyle) -> CardStyle) = edit { it.copy(style = f(it.style)) }
    fun undo() { history.undo()?.let { _card.value = it; _undo.value = history.canUndo to history.canRedo; rerender() } }
    fun redo() { history.redo()?.let { _card.value = it; _undo.value = history.canUndo to history.canRedo; rerender() } }
    fun setImage(id: String?) = viewModelScope.launch { palette = visuals.palette(id); edit { it.copy(imageAssetId = id) } }

    private fun rerender() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            delay(90)
            val c = _card.value ?: return@launch
            val bmp = withContext(Dispatchers.Default) {
                val img = media.loadBitmap(c.imageAssetId, 720)
                CardRenderer.render(c, 540, img, palette).also { img?.recycle() }
            }
            _preview.value = bmp
        }
    }

    fun save(done: () -> Unit) = viewModelScope.launch {
        val c = _card.value ?: return@launch
        _saving.value = true
        try {
            val b = repo.observeBundleOnce(projectId) ?: return@launch
            val cards = b.visualPlan?.cards.orEmpty().map { if (it.id == c.id) c else it }
            store.commit(projectId, b, cards, settings.current().cardExportWidth, setOf(c.id))
            done()
        } catch (e: Exception) { e.toAppException() } finally { _saving.value = false }
    }
}

private val swatches = listOf(null, 0xFFFFFFFF, 0xFFF4F5F9, 0xFFFFF6F0, 0xFFFFF9E6, 0xFF121218, 0xFF0B1220, 0xFF14120F, 0xFF5B4CF0, 0xFF00B89F, 0xFFE57A9A, 0xFFFF9F43).map { it?.toInt() }

@Composable
fun VisualEditorScreen(onBack: () -> Unit, vm: VisualEditorViewModel = hiltViewModel()) {
    val card by vm.card.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val undo by vm.undoState.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val wl = LocalWindowLayout.current
    Scaffold(topBar = {
        AppTopBar("Visual Editor", onBack) {
            IconButton(onClick = vm::undo, enabled = undo.first) { Icon(Icons.AutoMirrored.Filled.Undo, "실행 취소") }
            IconButton(onClick = vm::redo, enabled = undo.second) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
            Button(onClick = { vm.save(onBack) }, enabled = !saving, modifier = Modifier.padding(end = 8.dp)) { Text(if (saving) "저장 중…" else "저장") }
        }
    }) { pad ->
        val c = card ?: run { Box(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        val canvas: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), contentAlignment = Alignment.Center) {
                preview?.let { Image(it.asImageBitmap(), "카드 미리보기", Modifier.widthIn(max = 560.dp).fillMaxWidth().aspectRatio(c.style.aspect.w.toFloat() / c.style.aspect.h)) } ?: SparkIndicator()
            }
        }
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            if (wl.isCompact) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.45f)) { canvas() }
                    Box(Modifier.weight(0.55f)) { Properties(c, assets, vm) }
                }
            } else WorkspacePanes(left = { AssetLibrary(assets, c.imageAssetId, vm::setImage) }, center = canvas, right = { Properties(c, assets, vm, showAssets = !wl.isExpanded) })
        }
    }
}

@Composable
private fun AssetLibrary(assets: List<MediaAssetEntity>, selected: String?, onPick: (String?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Asset Library", style = MaterialTheme.typography.titleSmall)
        FilterChip(selected == null, { onPick(null) }, { Text("이미지 없음") })
        LazyVerticalGrid(GridCells.Adaptive(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(assets.filter { it.mimeType.startsWith("image/") && !it.usage.startsWith("card_") }, key = { it.id }) { a ->
                FileImage(a.path, Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.small).then(if (a.id == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier).clickable { onPick(a.id) }, contentDescription = a.kind.label)
            }
        }
    }
}

@Composable
private fun Properties(c: BlogCard, assets: List<MediaAssetEntity>, vm: VisualEditorViewModel, showAssets: Boolean = true) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(tab) { listOf("텍스트", "스타일", "이미지").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> {
                    OutlinedTextField(c.title, { v -> vm.edit(false) { it.copy(title = v) } }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(c.subtitle, { v -> vm.edit(false) { it.copy(subtitle = v) } }, label = { Text("부제") }, modifier = Modifier.fillMaxWidth())
                    if (c.rows.isEmpty()) OutlinedTextField(c.items.joinToString("\n"), { v -> vm.edit(false) { it.copy(items = v.lines()) } }, label = { Text("항목 (한 줄에 하나)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    else Text("정보 표는 검증된 원본 데이터로만 채워지며 직접 수정할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(c.cta, { v -> vm.edit(false) { it.copy(cta = v) } }, label = { Text("버튼 문구") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(c.style.brandText, { v -> vm.style { it.copy(brandText = v) } }, label = { Text("로고/브랜드 텍스트") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    LabeledSlider("글자 크기", c.style.fontScale, 0.6f..1.6f) { v -> vm.style { it.copy(fontScale = v) } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("굵게", Modifier.weight(1f)); Switch(c.style.bold, { v -> vm.style { it.copy(bold = v) } }) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextAlign.entries.forEach { a -> FilterChip(c.style.align == a, { vm.style { it.copy(align = a) } }, { Text(if (a == TextAlign.START) "왼쪽 정렬" else "가운데 정렬") }) } }
                }
                1 -> {
                    Text("프리셋", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CardStylePreset.entries.forEach { p -> FilterChip(c.style.preset == p, { vm.style { it.copy(preset = p) } }, { Text(p.label) }) } }
                    Text("비율", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AspectRatio.entries.forEach { a -> FilterChip(c.style.aspect == a, { vm.style { it.copy(aspect = a) } }, { Text(a.label) }) } }
                    Text("배경색 (대비는 자동 보정)", style = MaterialTheme.typography.labelLarge)
                    Swatches(c.style.backgroundArgb) { v -> vm.style { it.copy(backgroundArgb = v) } }
                    Text("강조색", style = MaterialTheme.typography.labelLarge)
                    Swatches(c.style.accentArgb) { v -> vm.style { it.copy(accentArgb = v) } }
                    LabeledSlider("여백", c.style.paddingDp.toFloat(), 24f..140f, { "%.0f".format(it) }) { v -> vm.style { it.copy(paddingDp = v.toInt()) } }
                    LabeledSlider("모서리 둥글기", c.style.cornerRadius.toFloat(), 0f..80f, { "%.0f".format(it) }) { v -> vm.style { it.copy(cornerRadius = v.toInt()) } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("그림자", Modifier.weight(1f)); Switch(c.style.shadow, { v -> vm.style { it.copy(shadow = v) } }) }
                    LabeledSlider("오버레이", c.style.overlayAlpha, 0f..0.8f) { v -> vm.style { it.copy(overlayAlpha = v) } }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("이미지 표시", Modifier.weight(1f)); Switch(c.style.showImage, { v -> vm.style { it.copy(showImage = v) } }) }
                    LabeledSlider("확대(Zoom/Crop)", c.style.imageZoom, 1f..3f) { v -> vm.style { it.copy(imageZoom = v) } }
                    LabeledSlider("가로 위치", c.style.imageOffsetX, -1f..1f) { v -> vm.style { it.copy(imageOffsetX = v) } }
                    LabeledSlider("세로 위치", c.style.imageOffsetY, -1f..1f) { v -> vm.style { it.copy(imageOffsetY = v) } }
                    if (showAssets) Box(Modifier.fillMaxWidth().size(320.dp)) { AssetLibrary(assets, c.imageAssetId, vm::setImage) }
                }
            }
        }
    }
}

@Composable
private fun Swatches(selected: Int?, onPick: (Int?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        swatches.forEach { argb ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(argb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceVariant)
                    .border(if (argb == selected) 3.dp else 1.dp, if (argb == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) { if (argb == null) Text("A", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
