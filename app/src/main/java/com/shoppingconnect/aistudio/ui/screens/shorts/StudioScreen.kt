package com.shoppingconnect.aistudio.ui.screens.shorts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.core.common.formatDurationMs
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.db.RenderState
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.ExportProfile
import com.shoppingconnect.aistudio.domain.model.FitMode
import com.shoppingconnect.aistudio.domain.model.MotionEffect
import com.shoppingconnect.aistudio.domain.model.RenderQuality
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.SfxType
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.TextAnimation
import com.shoppingconnect.aistudio.domain.model.TextStylePreset
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.domain.model.VideoResolution
import com.shoppingconnect.aistudio.domain.model.VoicePreset
import com.shoppingconnect.aistudio.media.video.SafeZone
import com.shoppingconnect.aistudio.ui.adaptive.LocalWindowLayout
import com.shoppingconnect.aistudio.ui.adaptive.WorkspacePanes
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.DemoBanner
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.LabeledSlider
import com.shoppingconnect.aistudio.ui.components.SparkIndicator
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(onBack: () -> Unit, onThumbnail: () -> Unit, vm: StudioViewModel = hiltViewModel()) {
    val s by vm.s.collectAsStateWithLifecycle()
    val frame by vm.frame.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val renders by vm.renders.collectAsStateWithLifecycle()
    val wl = LocalWindowLayout.current
    val ctx = LocalContext.current
    var showInspector by remember { mutableStateOf(false) }
    var showRender by remember { mutableStateOf(false) }
    var aiMenu by remember { mutableStateOf(false) }
    var replaceFor by remember { mutableStateOf<Int?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> u?.let { vm.importMedia(it, replaceFor) }; replaceFor = null }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { vm.importMedia(it, null) } }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { vm.importSrt(it, ctx) } }

    Scaffold(topBar = {
        AppTopBar("Shorts Studio", onBack) {
            IconButton(onClick = vm::undo, enabled = s.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "실행 취소") }
            IconButton(onClick = vm::redo, enabled = s.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 실행") }
            Box {
                TextButton(onClick = { aiMenu = true }) { Icon(Icons.Default.AutoAwesome, null); Text("AI 자동 편집") }
                DropdownMenu(aiMenu, { aiMenu = false }) {
                    listOf(15, 20, 30, 45, 60).forEach { d -> DropdownMenuItem({ Text("${d}초로 다시 만들기") }, { aiMenu = false; vm.autoEdit(d) }) }
                    DropdownMenuItem({ Text("Hook 후보 5개 생성") }, { aiMenu = false; vm.hooks() })
                    DropdownMenuItem({ Text("내레이션 AI 축약") }, { aiMenu = false; vm.aiShorten() })
                }
            }
            IconButton(onClick = { showRender = true }) { Icon(Icons.Default.VideoFile, "렌더 · 내보내기") }
        }
    }) { pad ->
        if (!s.loaded) { Box(Modifier.padding(pad).padding(24.dp)) { SparkIndicator() }; return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.isDemo) DemoBanner(Modifier.padding(8.dp))
            s.busy?.let { Column(Modifier.padding(horizontal = 16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, style = MaterialTheme.typography.labelMedium) } }
            if (s.timeline.scenes.isEmpty()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("아직 숏폼 드래프트가 없습니다.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { vm.autoEdit(30) }) { Icon(Icons.Default.AutoAwesome, null); Text("AI 숏폼 생성 (30초)") }
                    OutlinedButton(onClick = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text("직접 미디어로 시작") }
                }
                return@Column
            }
            val preview: @Composable () -> Unit = { Preview(s, frame, vm) }
            val timeline: @Composable () -> Unit = {
                Column {
                    Tools(s, vm, onAddMedia = { replaceFor = null; mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, onInspector = { showInspector = true }, compact = !wl.isExpanded)
                    TimelineView(s.timeline, s.playheadMs, s.zoom, s.selection, vm::seek, vm::select, vm::setZoom, Modifier.height(310.dp))
                }
            }
            if (wl.isCompact) {
                Box(Modifier.weight(1f).fillMaxWidth()) { preview() }
                timeline()
            } else {
                Box(Modifier.weight(1f)) {
                    WorkspacePanes(
                        left = { AssetsColumn(assets, onUse = { a -> (s.selection as? Sel.SceneSel)?.let { vm.replaceMedia(it.index, a) } }, onImport = { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) },
                        center = preview,
                        right = { Inspector(s, vm, assets, onReplace = { i -> replaceFor = i; mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, onMusic = { audioPicker.launch("audio/*") }, onSrt = { srtPicker.launch("*/*") }, onExportSrt = { share(ctx, vm) }) },
                    )
                }
                timeline()
            }
        }
    }
    if (showInspector) ModalBottomSheet(onDismissRequest = { showInspector = false }) {
        Box(Modifier.fillMaxWidth().height(520.dp).navigationBarsPadding()) {
            Inspector(s, vm, assets, onReplace = { i -> replaceFor = i; mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, onMusic = { audioPicker.launch("audio/*") }, onSrt = { srtPicker.launch("*/*") }, onExportSrt = { share(ctx, vm) })
        }
    }
    if (showRender) RenderSheet(s, renders, vm, onThumbnail = { showRender = false; onThumbnail() }) { showRender = false }
    s.recoverable?.let {
        AlertDialog(onDismissRequest = vm::discardRecovery, title = { Text("이전 편집 복구") }, text = { Text("저장되지 않은 편집 내용이 있습니다. 복구할까요?") },
            confirmButton = { TextButton(onClick = vm::recover) { Text("복구") } }, dismissButton = { TextButton(onClick = vm::discardRecovery) { Text("버리기") } })
    }
    s.message?.let { m -> AlertDialog(onDismissRequest = { vm.msg(null) }, text = { Text(m) }, confirmButton = { TextButton(onClick = { vm.msg(null) }) { Text("확인") } }) }
}

private fun share(ctx: android.content.Context, vm: StudioViewModel) {
    val f = vm.exportSrt()
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "SRT 내보내기"))
}

@Composable
private fun Preview(s: StudioState, frame: android.graphics.Bitmap?, vm: StudioViewModel) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E12)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).aspectRatio(9f / 16f), contentAlignment = Alignment.Center) {
            frame?.let { Image(it.asImageBitmap(), "미리보기 프레임", Modifier.fillMaxSize()) } ?: SparkIndicator()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = vm::togglePlay) { Icon(if (s.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (s.playing) "일시정지" else "재생") }
            Text("  ${s.playheadMs.formatDurationMs()} / ${s.timeline.durationMs.formatDurationMs()}", color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text("   Safe Zone", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Switch(s.timeline.showSafeZone, { v -> vm.update { it.copy(showSafeZone = v) } })
        }
        val outside = s.timeline.texts.filter { !SafeZone.isInside(0.5f, it.yFraction) }
        if (outside.isNotEmpty()) Text("⚠ Safe Zone 밖 텍스트 ${outside.size}개", color = Color(0xFFFFB020), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Tools(s: StudioState, vm: StudioViewModel, onAddMedia: () -> Unit, onInspector: () -> Unit, compact: Boolean) {
    var sticker by remember { mutableStateOf(false) }
    var sfx by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::split) { Icon(Icons.Default.ContentCut, "Split") }
        IconButton(onClick = vm::deleteSelected) { Icon(Icons.Default.Delete, "Delete") }
        IconButton(onClick = vm::duplicate) { Icon(Icons.Default.ContentCopy, "Duplicate") }
        IconButton(onClick = { vm.moveScene(-1) }) { Icon(Icons.Default.ChevronLeft, "앞으로 이동") }
        IconButton(onClick = { vm.moveScene(1) }) { Icon(Icons.Default.ChevronRight, "뒤로 이동") }
        TextButton(onClick = onAddMedia) { Text("+미디어") }
        TextButton(onClick = vm::addText) { Text("+텍스트") }
        Box { TextButton(onClick = { sticker = true }) { Text("+스티커") }; DropdownMenu(sticker, { sticker = false }) { listOf("🔥", "✨", "👍", "💯", "🛒", "⭐", "❤️", "👀").forEach { e -> DropdownMenuItem({ Text(e) }, { sticker = false; vm.addSticker(e) }) } } }
        Box { TextButton(onClick = { sfx = true }) { Text("+효과음") }; DropdownMenu(sfx, { sfx = false }) { SfxType.entries.forEach { t -> DropdownMenuItem({ Text(t.label) }, { sfx = false; vm.addSfx(t) }) } } }
        if (compact) IconButton(onClick = onInspector) { Icon(Icons.Default.Tune, "속성") }
    }
}

@Composable
private fun AssetsColumn(assets: List<MediaAssetEntity>, onUse: (MediaAssetEntity) -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Assets (탭: 선택한 장면에 적용)", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onImport) { Text("+ 사진/영상 가져오기") }
        LazyVerticalGrid(GridCells.Adaptive(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(assets.filter { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }, key = { it.id }) { a ->
                Card(onClick = { onUse(a) }) { FileImage(if (a.mimeType.startsWith("image/")) a.path else null, Modifier.fillMaxWidth().aspectRatio(1f), contentDescription = a.kind.label) }
            }
        }
    }
}

@Composable
private fun <T> Chips(title: String, values: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { v -> FilterChip(v == selected, { onPick(v) }, { Text(label(v)) }) } }
}

@Composable
private fun Inspector(s: StudioState, vm: StudioViewModel, assets: List<MediaAssetEntity>, onReplace: (Int) -> Unit, onMusic: () -> Unit, onSrt: () -> Unit, onExportSrt: () -> Unit) {
    val t = s.timeline
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val sel = s.selection) {
            is Sel.SceneSel -> t.scenes.getOrNull(sel.index)?.let { sc ->
                Text("Scene Inspector · 장면 ${sel.index + 1}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(sc.purpose, { v -> vm.updateScene(sel.index) { it.copy(purpose = v) } }, label = { Text("장면 목적") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(sc.caption, { v -> vm.updateScene(sel.index, retime = true) { it.copy(caption = v) } }, label = { Text("화면 텍스트") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sc.narration, { v -> vm.updateScene(sel.index) { it.copy(narration = v) } }, label = { Text("내레이션 (수정 후 음성 재생성)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileImage(sc.mediaPath?.takeIf { !sc.isVideo }, Modifier.size(56.dp))
                    TextButton(onClick = { onReplace(sel.index) }) { Text("미디어 교체 (Replace)") }
                }
                LabeledSlider("길이 (Duration)", sc.durationMs / 1000f, 0.5f..15f, { "%.1f초".format(it) }) { v -> vm.setSceneDuration(sel.index, (v * 1000).toLong()) }
                if (sc.isVideo) {
                    Row { TextButton(onClick = { vm.trimStart(sel.index, 500) }) { Text("앞 0.5초 자르기") }; TextButton(onClick = { vm.trimStart(sel.index, -500) }) { Text("앞 0.5초 복원") } }
                    LabeledSlider("속도", sc.speed, 0.5f..2f) { v -> vm.updateScene(sel.index) { it.copy(speed = v) } }
                    LabeledSlider("원본 음량", sc.volume, 0f..1f) { v -> vm.updateScene(sel.index) { it.copy(volume = v) } }
                }
                Chips("전환 (Transition)", TransitionType.entries, sc.transitionIn.type, { it.label }) { v -> vm.updateScene(sel.index, retime = true) { it.copy(transitionIn = it.transitionIn.copy(type = v)) } }
                LabeledSlider("전환 길이", sc.transitionIn.durationMs / 1000f, 0.1f..2f, { "%.1f초".format(it) }) { v -> vm.updateScene(sel.index, retime = true) { it.copy(transitionIn = it.transitionIn.copy(durationMs = (v * 1000).toLong())) } }
                Chips("모션 (Animation)", MotionEffect.entries, sc.motion, { it.label }) { v -> vm.updateScene(sel.index) { it.copy(motion = v) } }
                Chips("Fit / Fill", FitMode.entries, sc.fit, { if (it == FitMode.FIT) "Fit" else "Fill" }) { v -> vm.updateScene(sel.index) { it.copy(fit = v) } }
                LabeledSlider("Zoom / Crop", sc.zoom, 0.5f..3f) { v -> vm.updateScene(sel.index) { it.copy(zoom = v) } }
                LabeledSlider("가로 위치", sc.offsetX, -1f..1f) { v -> vm.updateScene(sel.index) { it.copy(offsetX = v) } }
                LabeledSlider("세로 위치", sc.offsetY, -1f..1f) { v -> vm.updateScene(sel.index) { it.copy(offsetY = v) } }
                Row { listOf(0, 90, 180, 270).forEach { r -> FilterChip(sc.rotation == r, { vm.updateScene(sel.index) { it.copy(rotation = r) } }, { Text("${r}°") }) } }
            }
            is Sel.TextSel -> t.texts.firstOrNull { it.id == sel.id }?.let { c ->
                Text("텍스트", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(c.text, { v -> vm.updateText(c.id) { it.copy(text = v) } }, modifier = Modifier.fillMaxWidth())
                Chips("스타일 프리셋", TextStylePreset.entries, c.style, { it.label }) { v -> vm.updateText(c.id) { it.copy(style = v) } }
                Chips("등장 애니메이션", TextAnimation.entries, c.enter, { it.label }) { v -> vm.updateText(c.id) { it.copy(enter = v) } }
                Chips("퇴장 애니메이션", TextAnimation.entries, c.exit, { it.label }) { v -> vm.updateText(c.id) { it.copy(exit = v) } }
                LabeledSlider("세로 위치", c.yFraction, 0f..1f) { v -> vm.updateText(c.id) { it.copy(yFraction = v) } }
                if (!SafeZone.isInside(0.5f, c.yFraction)) Text("⚠ Safe Zone 밖입니다. 플랫폼 UI에 가려질 수 있습니다.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                LabeledSlider("크기", c.sizeSp, 32f..110f, { "%.0f".format(it) }) { v -> vm.updateText(c.id) { it.copy(sizeSp = v) } }
                LabeledSlider("시작", c.startMs / 1000f, 0f..(t.durationMs / 1000f), { "%.1f초".format(it) }) { v -> vm.updateText(c.id) { it.copy(startMs = (v * 1000).toLong(), endMs = maxOf(it.endMs, (v * 1000).toLong() + 300)) } }
                LabeledSlider("끝", c.endMs / 1000f, 0f..(t.durationMs / 1000f), { "%.1f초".format(it) }) { v -> vm.updateText(c.id) { it.copy(endMs = maxOf((v * 1000).toLong(), it.startMs + 300)) } }
            }
            is Sel.SubSel -> t.subtitles.firstOrNull { it.id == sel.id }?.let { c ->
                Text("자막", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(c.text, { v -> vm.updateSub(c.id) { it.copy(text = v) } }, modifier = Modifier.fillMaxWidth())
                Text("강조 단어: ${c.highlights.joinToString().ifBlank { "없음 (상품 데이터에 있는 사실만 강조)" }}", style = MaterialTheme.typography.bodySmall)
            }
            is Sel.StickerSel -> t.stickers.firstOrNull { it.id == sel.id }?.let { c ->
                Text("스티커 ${c.emoji}", style = MaterialTheme.typography.titleMedium)
                LabeledSlider("가로", c.xFraction, 0f..1f) { v -> vm.update { tl -> tl.copy(stickers = tl.stickers.map { if (it.id == c.id) it.copy(xFraction = v) else it }) } }
                LabeledSlider("세로", c.yFraction, 0f..1f) { v -> vm.update { tl -> tl.copy(stickers = tl.stickers.map { if (it.id == c.id) it.copy(yFraction = v) else it }) } }
                LabeledSlider("크기", c.sizePx.toFloat(), 60f..300f, { "%.0f".format(it) }) { v -> vm.update { tl -> tl.copy(stickers = tl.stickers.map { if (it.id == c.id) it.copy(sizePx = v.toInt()) else it }) } }
            }
            is Sel.SfxSel -> t.sfx.firstOrNull { it.id == sel.id }?.let { c ->
                Text("효과음 ${c.label}", style = MaterialTheme.typography.titleMedium)
                LabeledSlider("볼륨", c.volume, 0f..1f) { v -> vm.update { tl -> tl.copy(sfx = tl.sfx.map { if (it.id == c.id) it.copy(volume = v) else it }) } }
                LabeledSlider("위치", c.startMs / 1000f, 0f..(t.durationMs / 1000f), { "%.1f초".format(it) }) { v -> vm.update { tl -> tl.copy(sfx = tl.sfx.map { if (it.id == c.id) it.copy(startMs = (v * 1000).toLong()) else it }) } }
            }
            else -> Unit
        }
        HorizontalDivider()
        Text("템플릿", style = MaterialTheme.typography.titleMedium)
        Chips("Shorts Template", ShortsTemplateId.entries, t.template, { it.label }) { vm.applyTemplate(it) }
        if (t.hookCandidates.isNotEmpty()) {
            Text("Hook 후보", style = MaterialTheme.typography.titleMedium)
            t.hookCandidates.forEach { h -> AssistChip(onClick = { vm.chooseHook(h) }, label = { Text("${h.type.label} · ${h.text}") }) }
        }
        HorizontalDivider()
        Text("자막", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("자막 표시", Modifier.weight(1f)); Switch(t.subtitlesEnabled, { v -> vm.update { it.copy(subtitlesEnabled = v) } }) }
        Chips("자막 스타일", SubtitleStyle.entries, t.subtitleStyle, { it.label }) { v -> vm.update { it.copy(subtitleStyle = v) } }
        Row { TextButton(onClick = onSrt) { Text("SRT 가져오기") }; TextButton(onClick = onExportSrt) { Text("SRT 내보내기") } }
        HorizontalDivider()
        Text("음성 (AI Voice)", style = MaterialTheme.typography.titleMedium)
        Chips("Voice Preset", VoicePreset.entries, t.voice.preset, { it.label }) { v -> vm.update { it.copy(voice = it.voice.copy(preset = v)) } }
        LabeledSlider("속도", t.voice.speed, 0.6f..1.6f) { v -> vm.update { it.copy(voice = it.voice.copy(speed = v)) } }
        LabeledSlider("피치", t.voice.pitch, 0.6f..1.6f) { v -> vm.update { it.copy(voice = it.voice.copy(pitch = v)) } }
        Button(onClick = vm::regenerateVoice) { Text("음성 다시 생성 + 타이밍 동기화") }
        HorizontalDivider()
        Text("음악 · 오디오 믹서", style = MaterialTheme.typography.titleMedium)
        Chips("AI BGM (앱 합성 음원)", BgmMood.entries, if (t.music == null) t.bgmMood else null, { it.label }) { v -> vm.update { it.copy(bgmMood = v, music = null) } }
        TextButton(onClick = onMusic) { Text(t.music?.let { "로컬 BGM 사용 중 · 교체" } ?: "로컬 BGM 선택") }
        MixRow("음성", t.mix.voice, { m -> vm.update { it.copy(mix = it.mix.copy(voice = m)) } })
        MixRow("음악", t.mix.music, { m -> vm.update { it.copy(mix = it.mix.copy(music = m)) } })
        MixRow("효과음", t.mix.sfx, { m -> vm.update { it.copy(mix = it.mix.copy(sfx = m)) } })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Auto Ducking (음성 시 BGM 감소)", Modifier.weight(1f)); Switch(t.mix.ducking, { v -> vm.update { it.copy(mix = it.mix.copy(ducking = v)) } }) }
        LabeledSlider("Ducking 레벨", t.mix.duckLevel, 0.1f..0.8f) { v -> vm.update { it.copy(mix = it.mix.copy(duckLevel = v)) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Master Limiter", Modifier.weight(1f)); Switch(t.mix.limiter, { v -> vm.update { it.copy(mix = it.mix.copy(limiter = v)) } }) }
    }
}

@Composable
private fun MixRow(label: String, m: com.shoppingconnect.aistudio.domain.model.TrackMix, onChange: (com.shoppingconnect.aistudio.domain.model.TrackMix) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            FilterChip(m.muted, { onChange(m.copy(muted = !m.muted)) }, { Text("Mute") })
            FilterChip(m.solo, { onChange(m.copy(solo = !m.solo)) }, { Text("Solo") })
        }
        LabeledSlider("볼륨", m.volume, 0f..1.5f) { onChange(m.copy(volume = it)) }
        LabeledSlider("Fade In", m.fadeInMs / 1000f, 0f..3f, { "%.1f초".format(it) }) { onChange(m.copy(fadeInMs = (it * 1000).toLong())) }
        LabeledSlider("Fade Out", m.fadeOutMs / 1000f, 0f..3f, { "%.1f초".format(it) }) { onChange(m.copy(fadeOutMs = (it * 1000).toLong())) }
        LabeledSlider("Pan", m.pan, -1f..1f) { onChange(m.copy(pan = it)) }
    }
}

@Composable
private fun RenderSheet(s: StudioState, renders: List<com.shoppingconnect.aistudio.data.db.RenderJobEntity>, vm: StudioViewModel, onThumbnail: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var rs by remember { mutableStateOf(s.timeline.render) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("렌더 · 내보내기", style = MaterialTheme.typography.titleLarge)
            Chips("해상도", VideoResolution.entries, rs.resolution, { it.label }) { rs = rs.copy(resolution = it) }
            Chips("FPS", listOf(30, 60), rs.fps, { "${it}fps" }) { rs = rs.copy(fps = it) }
            Chips("Render Profile", RenderQuality.entries, rs.quality, { it.label }) { rs = rs.copy(quality = it) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("HEVC (지원 기기만, 미지원 시 H.264)", Modifier.weight(1f)); Switch(rs.hevc, { rs = rs.copy(hevc = it) }) }
            Chips("Export Profile", ExportProfile.entries, rs.exportProfile, { it.label }) { rs = rs.copy(exportProfile = it) }
            if (s.timeline.durationMs / 1000 > rs.exportProfile.maxDurationSec) Text("⚠ ${rs.exportProfile.label} 권장 길이(${rs.exportProfile.maxDurationSec}초)를 초과합니다.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { vm.render(rs) }, Modifier.fillMaxWidth()) { Text("렌더링 시작 (${rs.resolution.width}×${rs.resolution.height})") }
            OutlinedButton(onClick = onThumbnail, Modifier.fillMaxWidth()) { Text("썸네일 편집 (Thumbnail Studio)") }
            Text("Render Queue", style = MaterialTheme.typography.titleMedium)
            renders.take(8).forEach { r ->
                Card { Column(Modifier.padding(10.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (r.state) { RenderState.QUEUED -> "Waiting"; RenderState.RENDERING -> "Rendering ${r.progress}%"; RenderState.SUCCEEDED -> "완료 · ${r.frames}프레임 · ${r.renderMs / 1000}초 소요"; RenderState.FAILED -> "실패: ${r.errorMessage ?: ""}"; RenderState.CANCELLED -> "취소됨" }, Modifier.weight(1f))
                        if (r.state == RenderState.QUEUED || r.state == RenderState.RENDERING) TextButton(onClick = { vm.cancelRender(r.id) }) { Text("취소") }
                    }
                    if (r.state == RenderState.RENDERING) LinearProgressIndicator(progress = { r.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    if (r.state == RenderState.SUCCEEDED && r.outputPath != null) {
                        VideoPlayer(r.outputPath, Modifier.fillMaxWidth().height(360.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.saveToGallery(r.outputPath) }) { Text("기기에 저장") }
                            OutlinedButton(onClick = { scope.launch { ctx.startActivity(vm.shareIntent(r.outputPath)) } }) { Text("공유") }
                        }
                    }
                } }
            }
            Box(Modifier.height(24.dp).fillMaxHeight())
        }
    }
}
