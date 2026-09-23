package com.shoppingconnect.aistudio.ui.screens.manual

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.db.GenerationDao
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.domain.model.ContentStrategy
import com.shoppingconnect.aistudio.domain.model.PipelineOptions
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.pipeline.PipelineOrchestrator
import com.shoppingconnect.aistudio.pipeline.WorkScheduler
import com.shoppingconnect.aistudio.product.ManualProductAdapter
import com.shoppingconnect.aistudio.product.NaverShoppingSearchApi
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.SectionCard
import com.shoppingconnect.aistudio.ui.screens.home.UsageDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualState(
    val input: ManualProductAdapter.Input,
    val images: List<Uri> = emptyList(),
    val query: String = "",
    val results: List<NaverShoppingSearchApi.Item> = emptyList(),
    val searching: Boolean = false,
    val busy: Boolean = false,
    val askUsage: Boolean = false,
    val pickedFromSearch: Product? = null,
    val error: String? = null,
)

@HiltViewModel
class ManualInputViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val scheduler: WorkScheduler,
    val search: NaverShoppingSearchApi,
    private val projects: ProjectRepository,
    private val media: MediaRepository,
    private val generations: GenerationDao,
    private val orchestrator: PipelineOrchestrator,
) : ViewModel() {
    private val genId: String = handle.get<String>("genId").orEmpty()
    private val _s = MutableStateFlow(ManualState(ManualProductAdapter.Input(url = handle.get<String>("url").orEmpty(), title = "")))
    val s = _s.asStateFlow()
    private val _done = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    fun edit(f: (ManualProductAdapter.Input) -> ManualProductAdapter.Input) = _s.update { it.copy(input = f(it.input), error = null) }
    fun setImages(u: List<Uri>) = _s.update { it.copy(images = (it.images + u).distinct().take(10)) }
    fun query(q: String) = _s.update { it.copy(query = q) }

    fun runSearch() = viewModelScope.launch {
        _s.update { it.copy(searching = true, error = null) }
        try { val r = search.search(_s.value.query); _s.update { it.copy(results = r, searching = false) } }
        catch (e: Exception) { _s.update { it.copy(searching = false, error = e.toAppException().userMessage) } }
    }

    fun pick(item: NaverShoppingSearchApi.Item) {
        val p = search.toProduct(item, _s.value.input.url)
        _s.update { it.copy(pickedFromSearch = p, input = it.input.copy(title = p.title, brand = p.brand.orEmpty(), category = p.category.orEmpty(), price = p.price?.toString().orEmpty(), url = it.input.url.ifBlank { p.affiliateUrl })) }
    }

    fun requestSubmit() {
        if (_s.value.input.title.isBlank()) { _s.update { it.copy(error = "상품명을 입력해 주세요.") }; return }
        _s.update { it.copy(askUsage = true) }
    }
    fun cancelUsage() = _s.update { it.copy(askUsage = false) }

    fun submit(usage: com.shoppingconnect.aistudio.domain.model.UsageStatus) = viewModelScope.launch {
        _s.update { it.copy(askUsage = false, busy = true) }
        try {
            val st = _s.value
            val manual = ManualProductAdapter.build(st.input)
            // Search-API facts stay verified; user-typed values override where given.
            val product = st.pickedFromSearch?.let { sp ->
                manual.copy(images = sp.images, source = sp.source, verifiedFields = (manual.verifiedFields + sp.verifiedFields).distinct(),
                    uncertainFields = manual.uncertainFields.filterNot { it in sp.verifiedFields }, affiliateUrl = st.input.url.ifBlank { sp.affiliateUrl })
            } ?: manual
            val options = PipelineOptions(strategy = ContentStrategy(usage = usage))
            val existing = generations.get(genId)
            val started = if (existing != null) {
                projects.saveProduct(existing.projectId, product.copy(id = projects.product(existing.projectId)?.id ?: product.id), null)
                st.images.forEach { runCatching { media.importUri(existing.projectId, it) } }
                scheduler.startGeneration(existing.projectId, product.affiliateUrl, orchestrator.decodeOptions(existing).copy(strategy = options.strategy), null)
            } else {
                val r = scheduler.startWithProduct(product.copy(images = emptyList()), options)
                st.images.forEach { runCatching { media.importUri(r.projectId, it) } }
                r
            }
            _done.emit(started.generationId)
        } catch (e: Exception) {
            _s.update { it.copy(busy = false, error = e.toAppException().userMessage) }
        }
    }
}

@Composable
fun ManualInputScreen(onBack: () -> Unit, onStarted: (String) -> Unit, vm: ManualInputViewModel = hiltViewModel()) {
    val s by vm.s.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { vm.setImages(it) }
    LaunchedEffect(Unit) { vm.done.collect { onStarted(it) } }
    Scaffold(topBar = { AppTopBar("상품 정보 직접 입력", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.search.isConfigured) SectionCard(title = "네이버 쇼핑 검색 API로 찾기") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(s.query, vm::query, Modifier.weight(1f), singleLine = true, label = { Text("상품명 검색") })
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.runSearch() }, enabled = !s.searching) { Icon(Icons.Default.Search, "검색") }
                    }
                    s.results.take(10).forEach { item ->
                        Card(onClick = { vm.pick(item) }) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(item.image, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(org.jsoup.Jsoup.parse(item.title).text(), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    Text("${item.mallName} · ${formatPrice(item.lprice.toLongOrNull()) ?: "가격 정보 없음"}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                SectionCard(title = "상품 정보") {
                    Text("입력한 정보만 사실로 사용합니다. 비워 둔 항목은 'unknown'으로 처리되며 AI가 추측하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(s.input.url, { v -> vm.edit { it.copy(url = v) } }, label = { Text("쇼핑 커넥트 / 상품 링크") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(s.input.title, { v -> vm.edit { it.copy(title = v) } }, label = { Text("상품명 *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(s.input.brand, { v -> vm.edit { it.copy(brand = v) } }, label = { Text("브랜드") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(s.input.category, { v -> vm.edit { it.copy(category = v) } }, label = { Text("카테고리") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(s.input.price, { v -> vm.edit { it.copy(price = v) } }, label = { Text("판매가(원)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(s.input.originalPrice, { v -> vm.edit { it.copy(originalPrice = v) } }, label = { Text("정가(원)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(s.input.description, { v -> vm.edit { it.copy(description = v) } }, label = { Text("상품 설명") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(s.input.specs, { v -> vm.edit { it.copy(specs = v) } }, label = { Text("스펙 (한 줄에 '항목: 값')") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                SectionCard(title = "상품 이미지 (사용 권한이 있는 이미지)") {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("사진 선택 (Photo Picker)")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(s.images) { AsyncImage(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) } }
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::requestSubmit, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(if (s.busy) "시작하는 중…" else "AI 콘텐츠 만들기") }
            }
        }
    }
    if (s.askUsage) UsageDialog(onPick = vm::submit, onDismiss = vm::cancelUsage)
}
