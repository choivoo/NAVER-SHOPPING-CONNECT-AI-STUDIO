package com.shoppingconnect.aistudio.ui.screens.shorts

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.DemoAiEngine
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.media.shorts.ShortsService
import com.shoppingconnect.aistudio.media.shorts.ThumbnailRenderer
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.FileImage
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ThumbnailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val media: MediaRepository,
    private val shorts: ShortsService,
    private val engines: AiEngineProvider,
    private val exporter: MediaExporter,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    val assets = media.observe(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _spec = MutableStateFlow(ThumbnailSpec())
    val spec = _spec.asStateFlow()
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview = _preview.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val b = repo.observeBundleOnce(projectId)
            _spec.value = b?.thumbnail ?: ThumbnailSpec(title = b?.product?.title?.take(12).orEmpty(), imageAssetId = b?.assets?.firstOrNull { it.kind == AssetKind.ORIGINAL }?.id)
            render()
        }
    }

    fun edit(f: (ThumbnailSpec) -> ThumbnailSpec) { _spec.value = f(_spec.value); render() }

    private fun render() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(80)
            _preview.value = withContext(Dispatchers.Default) {
                val img = media.loadBitmap(_spec.value.imageAssetId, 720)
                ThumbnailRenderer.render(_spec.value, img, 540).also { img?.recycle() }
            }
        }
    }

    fun aiTexts() = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        val p = b.product ?: return@launch
        try {
            val t = (if (b.project.isDemo) DemoAiEngine() else engines.engine()).thumbnailTexts(p)
            edit { it.copy(candidates = (t.titles + t.hooks).distinct()) }
        } catch (e: Exception) { _msg.value = e.toAppException().userMessage }
    }

    fun save(toGallery: Boolean) = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        val asset = shorts.renderThumbnail(projectId, _spec.value)
        b.timeline?.let { repo.saveShortform(projectId, it, _spec.value, thumbnailPath = asset.path) }
        if (toGallery) runCatching { exporter.saveImage(File(asset.path), "${b.project.title.take(24)}_thumbnail.jpg") }
        _msg.value = if (toGallery) "썸네일을 저장하고 갤러리에 내보냈습니다." else "썸네일을 저장했습니다."
    }
}

@Composable
fun ThumbnailStudioScreen(onBack: () -> Unit, vm: ThumbnailViewModel = hiltViewModel()) {
    val spec by vm.spec.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Thumbnail Studio", onBack) }) { pad ->
        Row(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).padding(12.dp)) { preview?.let { Image(it.asImageBitmap(), "썸네일 미리보기", Modifier.widthIn(max = 360.dp).aspectRatio(9f / 16f)) } }
            Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(spec.title, { v -> vm.edit { it.copy(title = v) } }, label = { Text("큰 제목") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(spec.hook, { v -> vm.edit { it.copy(hook = v) } }, label = { Text("짧은 Hook") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(spec.badge, { v -> vm.edit { it.copy(badge = v) } }, label = { Text("배지") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                AssistChip(onClick = vm::aiTexts, label = { Text("AI 문구 후보 5개") })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { spec.candidates.forEach { c -> AssistChip(onClick = { vm.edit { it.copy(title = c) } }, label = { Text(c) }) } }
                Text("레이아웃", style = MaterialTheme.typography.labelLarge)
                Row { (0 until ThumbnailRenderer.LAYOUTS).forEach { i -> FilterChip(spec.layout == i, { vm.edit { it.copy(layout = i) } }, { Text("L${i + 1}") }) } }
                Text("스티커", style = MaterialTheme.typography.labelLarge)
                Row { listOf("", "🔥", "✨", "💯", "👀", "⭐").forEach { e -> FilterChip(spec.sticker == e, { vm.edit { it.copy(sticker = e) } }, { Text(e.ifBlank { "없음" }) }) } }
                Text("배경 그라디언트", style = MaterialTheme.typography.labelLarge)
                Row {
                    listOf(0xFF5B4CF0 to 0xFF101014, 0xFFE8173B to 0xFF1A0A0F, 0xFF00B89F to 0xFF06201C, 0xFF222222 to 0xFF000000, 0xFFFF9F43 to 0xFF2A1400).forEach { (a, b) ->
                        FilterChip(spec.gradientTopArgb == a.toInt(), { vm.edit { it.copy(gradientTopArgb = a.toInt(), gradientBottomArgb = b.toInt()) } }, { Text("●") })
                    }
                }
                Text("상품 이미지", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    assets.filter { it.mimeType.startsWith("image/") && it.kind != AssetKind.THUMBNAIL }.take(12).forEach { a ->
                        FileImage(a.path, Modifier.size(56.dp).clickable { vm.edit { it.copy(imageAssetId = a.id) } }, contentDescription = a.kind.label)
                    }
                }
                Button(onClick = { vm.save(false) }, Modifier.fillMaxWidth()) { Text("저장") }
                Button(onClick = { vm.save(true) }, Modifier.fillMaxWidth()) { Text("저장 + 갤러리로 내보내기") }
                msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
