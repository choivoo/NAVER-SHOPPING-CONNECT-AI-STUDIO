package com.shoppingconnect.aistudio.ui.screens.editor

import android.content.ClipData
import android.content.ClipDescription
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.ai.RewriteAction
import com.shoppingconnect.aistudio.core.common.formatDateTime
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.TitleCategory
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.WorkspacePanes
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.CheckRow
import com.shoppingconnect.aistudio.ui.components.DemoBanner
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.KeyValue
import com.shoppingconnect.aistudio.ui.components.ScoreRing
import com.shoppingconnect.aistudio.ui.components.SeverityIcon
import com.shoppingconnect.aistudio.ui.components.SparkIndicator

private enum class Panel(val label: String) { AI("AI"), SEO("SEO"), FACTS("사실"), CHECK("검사"), ASSETS("에셋") }

@Composable
fun EditorScreen(onBack: () -> Unit, onPreview: () -> Unit, onPublish: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val s by vm.s.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val wl = LocalWindowLayout.current
    var sheet by remember { mutableStateOf<Panel?>(null) }
    var showTitles by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::importImage) }

    Scaffold(topBar = {
        AppTopBar("블로그 편집기", onBack) {
            IconButton(onClick = vm::undo, enabled = s.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "실행 취소") }
            IconButton(onClick = vm::redo, enabled = s.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
            IconButton(onClick = { showVersions = true }) { Icon(Icons.Default.History, "버전 기록") }
            IconButton(onClick = onPreview) { Icon(Icons.Default.Visibility, "미리보기") }
            IconButton(onClick = onPublish) { Icon(Icons.Default.Publish, "게시 준비") }
        }
    }) { pad ->
        if (!s.loaded) { Column(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            WorkspacePanes(
                left = { AssetsPanel(assets, onInsert = { vm.insert(BlockType.IMAGE, it) }, onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) },
                center = {
                    Column(Modifier.fillMaxSize().imePadding()) {
                        EditorCanvas(s, assets, vm, onTitles = { showTitles = true }, modifier = Modifier.weight(1f))
                        Toolbar(vm, onImage = { if (wl.isExpanded) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else sheet = Panel.ASSETS }, onAi = { sheet = Panel.AI }, compact = !wl.isExpanded, onPanel = { sheet = it })
                    }
                },
                right = { Inspector(s, vm) },
            )
        }
    }
    sheet?.let { p ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                when (p) {
                    Panel.ASSETS -> AssetsPanel(assets, onInsert = { vm.insert(BlockType.IMAGE, it); sheet = null }, onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)); sheet = null })
                    else -> Inspector(s, vm, initial = p)
                }
            }
        }
    }
    if (showTitles) TitlesSheet(s, vm) { showTitles = false }
    if (showVersions) AlertDialog(
        onDismissRequest = { showVersions = false }, title = { Text("버전 기록") },
        text = {
            LazyColumn(Modifier.widthIn(max = 480.dp)) {
                item { TextButton(onClick = { vm.saveVersion("User Edit") }) { Text("현재 상태를 버전으로 저장") } }
                item { TextButton(onClick = { vm.saveVersion("Final") }) { Text("Final로 저장") } }
                items(versions, key = { it.id }) { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(v.label, style = MaterialTheme.typography.bodyMedium); Text(v.createdAt.formatDateTime(), style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { vm.restore(v); showVersions = false }) { Text("복원") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showVersions = false }) { Text("닫기") } },
    )
    s.message?.let { m -> AlertDialog(onDismissRequest = vm::dismissMessage, text = { Text(m) }, confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("확인") } }) }
}

@Composable
private fun EditorCanvas(s: EditorState, assets: List<MediaAssetEntity>, vm: EditorViewModel, onTitles: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.isDemo) DemoBanner()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = s.article.title, onValueChange = vm::updateTitle, modifier = Modifier.weight(1f), placeholder = { Text("제목") },
                        textStyle = MaterialTheme.typography.headlineSmall, colors = transparentFieldColors(),
                    )
                    TextButton(onClick = onTitles) { Icon(Icons.Default.Title, null); Text("제목 후보") }
                }
                Text(if (s.saving) "저장 중…" else if (s.savedAt > 0) "자동 저장됨 ${s.savedAt.formatDateTime()}" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
        }
        items(s.article.blocks, key = { it.id }) { b ->
            BlockRow(b, selected = b.id == s.selectedId, assets = assets, vm = vm, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth())
        }
        item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(top = 12.dp)) {
                var tags by remember(s.article.hashtags) { mutableStateOf(s.article.hashtags.joinToString(" ") { "#$it" }) }
                OutlinedTextField(tags, { tags = it; vm.updateHashtags(it) }, label = { Text("해시태그") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun BlockRow(b: Block, selected: Boolean, assets: List<MediaAssetEntity>, vm: EditorViewModel, modifier: Modifier) {
    val dropTarget = remember(b.id) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clip = event.toAndroidDragEvent().clipData ?: return false
                val text = clip.getItemAt(0).text?.toString() ?: return false
                if (!text.startsWith("asset:")) return false
                vm.insertImageAt(text.removePrefix("asset:"), b.id)
                return true
            }
        }
    }
    val border = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier
    Column(
        modifier.then(border).clip(RoundedCornerShape(12.dp))
            .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target = dropTarget)
            .padding(4.dp),
    ) {
        when (b.type) {
            BlockType.H1, BlockType.H2, BlockType.PARAGRAPH, BlockType.QUOTE, BlockType.DISCLOSURE, BlockType.CTA -> {
                val style = when (b.type) {
                    BlockType.H1 -> MaterialTheme.typography.headlineSmall
                    BlockType.H2 -> MaterialTheme.typography.titleLarge
                    BlockType.QUOTE -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BlockType.DISCLOSURE -> MaterialTheme.typography.bodySmall
                    else -> MaterialTheme.typography.bodyLarge
                }
                if (b.type == BlockType.DISCLOSURE) Text("광고/제휴 표시", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                if (b.type == BlockType.CTA) Text("CTA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (b.type == BlockType.QUOTE) Text("인용", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                BlockTextField(b.id, b.text, style, onChange = { t, sel -> vm.updateBlock(b.id) { it.copy(text = t) }; vm.select(b.id, sel) }, onFocus = { vm.select(b.id) })
            }
            BlockType.LIST -> {
                BlockTextField(b.id, b.items.joinToString("\n"), MaterialTheme.typography.bodyLarge, prefix = "• ",
                    onChange = { t, _ -> vm.updateBlock(b.id) { it.copy(items = t.split('\n')) }; vm.select(b.id) }, onFocus = { vm.select(b.id) })
            }
            BlockType.IMAGE -> {
                val a = assets.firstOrNull { it.id == b.assetId }
                FileImage(a?.path, Modifier.fillMaxWidth().aspectRatio(a?.aspectRatio?.takeIf { it > 0.2f } ?: 1f).clip(RoundedCornerShape(10.dp)), crop = false)
                Text(a?.let { "${it.copyrightType.label}${if (it.aiGenerated) " · 생성 그래픽" else ""}" } ?: "이미지를 찾을 수 없음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { vm.select(b.id) }) { Text("선택") }
            }
            BlockType.DIVIDER -> { HorizontalDivider(Modifier.padding(vertical = 12.dp)); TextButton(onClick = { vm.select(b.id) }) { Text("구분선 선택") } }
            BlockType.LINK -> {
                OutlinedTextField(b.text, { t -> vm.updateBlock(b.id) { it.copy(text = t) } }, label = { Text("링크 텍스트") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b.url.orEmpty(), { t -> vm.updateBlock(b.id) { it.copy(url = t) } }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { vm.select(b.id) }) { Text("선택") }
            }
            BlockType.PRODUCT_CARD -> Card(onClick = { vm.select(b.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("${b.text} · 검증된 원본 데이터", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::refreshProductCard) { Text("원본으로 갱신") }
                    }
                    b.items.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (selected) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IconButton(onClick = { vm.move(b.id, -1) }) { Icon(Icons.Default.ArrowUpward, "위로 이동") }
            IconButton(onClick = { vm.move(b.id, 1) }) { Icon(Icons.Default.ArrowDownward, "아래로 이동") }
            if (b.type in setOf(BlockType.PARAGRAPH, BlockType.H2, BlockType.QUOTE, BlockType.LIST)) {
                listOf(BlockType.PARAGRAPH, BlockType.H2, BlockType.QUOTE, BlockType.LIST).filter { it != b.type }.forEach { t -> AssistChip(onClick = { vm.changeType(b.id, t) }, label = { Text("→${t.label}") }) }
            }
            IconButton(onClick = { vm.delete(b.id) }) { Icon(Icons.Default.Close, "블록 삭제") }
        }
    }
}

@Composable
private fun BlockTextField(id: String, text: String, style: TextStyle, prefix: String? = null, onChange: (String, IntRange?) -> Unit, onFocus: () -> Unit) {
    var tfv by remember(id) { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) { if (text != tfv.text) tfv = TextFieldValue(text, TextRange(text.length)) }
    TextField(
        value = tfv,
        onValueChange = { v ->
            val changed = v.text != tfv.text
            tfv = v
            val sel = if (v.selection.collapsed) null else v.selection.min until v.selection.max
            if (changed) onChange(v.text, sel) else onChange(v.text, sel)
        },
        modifier = Modifier.fillMaxWidth().onFocusSelect(onFocus),
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        prefix = prefix?.let { { Text(it) } },
        colors = transparentFieldColors(),
    )
}

private fun Modifier.onFocusSelect(onFocus: () -> Unit): Modifier = this.then(
    Modifier.onFocusChanged { if (it.isFocused) onFocus() },
)

@Composable
private fun Toolbar(vm: EditorViewModel, onImage: () -> Unit, onAi: () -> Unit, compact: Boolean, onPanel: (Panel) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.insert(BlockType.H1) }) { Text("H1", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { vm.insert(BlockType.H2) }) { Text("H2", fontWeight = FontWeight.Bold) }
            IconButton(onClick = vm::bold) { Icon(Icons.Default.FormatBold, "굵게") }
            IconButton(onClick = { vm.insert(BlockType.QUOTE) }) { Icon(Icons.Default.FormatQuote, "인용") }
            IconButton(onClick = { vm.insert(BlockType.LIST) }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "목록") }
            IconButton(onClick = onImage) { Icon(Icons.Default.Image, "이미지") }
            IconButton(onClick = { vm.insert(BlockType.DIVIDER) }) { Icon(Icons.Default.HorizontalRule, "구분선") }
            IconButton(onClick = { vm.insert(BlockType.LINK) }) { Icon(Icons.Default.Link, "링크") }
            IconButton(onClick = { vm.insert(BlockType.PRODUCT_CARD) }) { Icon(Icons.Default.ShoppingBag, "상품 카드") }
            IconButton(onClick = { vm.insert(BlockType.CTA) }) { Icon(Icons.Default.Campaign, "CTA") }
            TextButton(onClick = { vm.insert(BlockType.DISCLOSURE) }) { Text("Disclosure") }
            TextButton(onClick = onAi) { Icon(Icons.Default.AutoAwesome, null); Text("AI") }
            if (compact) {
                IconButton(onClick = { onPanel(Panel.SEO) }) { Icon(Icons.Default.Insights, "SEO Inspector") }
                IconButton(onClick = { onPanel(Panel.CHECK) }) { Icon(Icons.AutoMirrored.Filled.FactCheck, "검사") }
                IconButton(onClick = { onPanel(Panel.FACTS) }) { Icon(Icons.Default.Verified, "상품 사실") }
            }
        }
    }
}

@Composable
private fun AssetsPanel(assets: List<MediaAssetEntity>, onInsert: (String) -> Unit, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("에셋 (탭: 삽입 · 길게 끌기: 원하는 위치에 놓기)", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onPick) { Icon(Icons.Default.AddPhotoAlternate, null); Text("사진 추가") }
        LazyVerticalGrid(GridCells.Adaptive(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(assets.filter { it.mimeType.startsWith("image/") }, key = { it.id }) { a ->
                Column(
                    Modifier.dragAndDropSource { _ -> DragAndDropTransferData(ClipData.newPlainText("asset", "asset:${a.id}")) },
                ) {
                    Card(onClick = { onInsert(a.id) }) { FileImage(a.path, Modifier.fillMaxWidth().aspectRatio(1f), contentDescription = "${a.kind.label} 이미지") }
                    Text(a.kind.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Inspector(s: EditorState, vm: EditorViewModel, initial: Panel = Panel.AI) {
    var tab by rememberSaveable { mutableIntStateOf(listOf(Panel.AI, Panel.SEO, Panel.FACTS, Panel.CHECK).indexOf(initial).coerceAtLeast(0)) }
    val tabs = listOf(Panel.AI, Panel.SEO, Panel.FACTS, Panel.CHECK)
    Column(Modifier.fillMaxWidth()) {
        PrimaryTabRow(selectedTabIndex = tab) { tabs.forEachIndexed { i, p -> Tab(tab == i, { tab = i }, text = { Text(if (p == Panel.CHECK) "검사 ${s.issues.size}" else p.label) }) } }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tabs[tab]) {
                Panel.AI -> {
                    Text(if (s.selectedId == null) "편집할 문단을 선택하세요. 문장 일부를 드래그로 선택하면 그 부분만 다듬습니다." else "선택한 블록에 적용됩니다.", style = MaterialTheme.typography.bodySmall)
                    if (s.aiBusy) SparkIndicator()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RewriteAction.entries.forEach { a -> AssistChip(onClick = { vm.rewrite(a) }, enabled = !s.aiBusy && s.selectedId != null, label = { Text(a.label) }) }
                    }
                    TextButton(onClick = vm::splitLongParagraphs) { Text("긴 문단 자동 분리") }
                    if (s.repeated.isNotEmpty()) { Text("반복되는 문장", style = MaterialTheme.typography.labelLarge); s.repeated.forEach { Text("• $it…", style = MaterialTheme.typography.bodySmall) } }
                }
                Panel.SEO -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(s.quality.score, "Quality")
                        Spacer(Modifier.width(12.dp))
                        Text("Content Quality Score는 글 품질 점검용 지표이며, 검색 노출이나 순위를 보장하거나 예측하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    var kw by remember(s.strategy.primaryKeyword) { mutableStateOf(s.strategy.primaryKeyword) }
                    OutlinedTextField(kw, { kw = it; vm.setKeyword(it) }, label = { Text("핵심 키워드") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    s.quality.checks.forEach { CheckRow(it.passed, it.name, it.detail) }
                }
                Panel.FACTS -> FactsPanel(s.product)
                Panel.CHECK -> {
                    if (s.issues.isEmpty()) CheckRow(true, "발견된 문제 없음", "과장 표현, 허위 사용 후기, 원본과 다른 수치, 광고 표시를 검사했습니다.")
                    s.issues.forEach { i ->
                        Card { Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { SeverityIcon(i.severity); Spacer(Modifier.width(6.dp)); Text(i.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)) }
                            i.excerpt?.let { Text("\"$it\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            i.suggestion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row {
                                if (i.code == "FAKE_EXPERIENCE" || i.code == "MISSING_DISCLOSURE") TextButton(onClick = { vm.fix(i) }) { Text("자동 수정") }
                                if (i.blockId != null) TextButton(onClick = { vm.select(i.blockId) }) { Text("해당 블록으로") }
                            }
                        } }
                    }
                }
                Panel.ASSETS -> Unit
            }
        }
    }
}

/** Separates verified SOURCE DATA from AI-written text. */
@Composable
private fun FactsPanel(p: Product?) {
    if (p == null) { Text("상품 정보 없음"); return }
    Text("검증된 상품 사실만 글의 사실 근거로 사용됩니다. AI가 쓴 문장 속 가격·할인·스펙은 이 값과 비교해 검사합니다.", style = MaterialTheme.typography.bodySmall)
    KeyValue("상품명", p.title)
    KeyValue("브랜드", p.brand, !p.isVerified(Product.F_BRAND))
    KeyValue("가격", com.shoppingconnect.aistudio.core.common.formatPrice(p.price), !p.isVerified(Product.F_PRICE))
    KeyValue("정가", com.shoppingconnect.aistudio.core.common.formatPrice(p.originalPrice), !p.isVerified(Product.F_ORIGINAL_PRICE))
    p.specifications.forEach { KeyValue(it.name, it.value) }
    KeyValue("출처", "${p.source.label} · ${p.extractionTimestamp.formatDateTime()} 확인")
}

@Composable
private fun TitlesSheet(s: EditorState, vm: EditorViewModel, onDismiss: () -> Unit) {
    var cat by remember { mutableStateOf<TitleCategory?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("제목 후보 (A/B)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::regenerateTitles, enabled = !s.titleBusy) { Text(if (s.titleBusy) "생성 중…" else "다시 생성") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(cat == null, { cat = null }, { Text("전체") })
                TitleCategory.entries.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c.label) }) }
            }
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                items(s.article.titleCandidates.filter { cat == null || it.category == cat }, key = { it.id }) { t ->
                    Card(onClick = { vm.chooseTitle(t); onDismiss() }, Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = if (t.text == s.article.title) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.text, style = MaterialTheme.typography.bodyLarge)
                            Text("${t.category.label} · ${t.text.length}자", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
