package com.shoppingconnect.aistudio.ui.screens.shorts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.ai.AiEngineProvider
import com.shoppingconnect.aistudio.ai.DemoAiEngine
import com.shoppingconnect.aistudio.core.common.AppLog
import com.shoppingconnect.aistudio.core.common.toAppException
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.data.db.RenderJobDao
import com.shoppingconnect.aistudio.data.db.RenderJobEntity
import com.shoppingconnect.aistudio.data.files.ProjectFiles
import com.shoppingconnect.aistudio.data.repository.MediaRepository
import com.shoppingconnect.aistudio.data.repository.ProjectRepository
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.domain.model.AssetKind
import com.shoppingconnect.aistudio.domain.model.AudioClip
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SfxType
import com.shoppingconnect.aistudio.domain.model.ThumbnailSpec
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.media.audio.AudioDecoder
import com.shoppingconnect.aistudio.media.audio.AudioMixer
import com.shoppingconnect.aistudio.media.audio.BgmSynth
import com.shoppingconnect.aistudio.media.audio.PlacedAudio
import com.shoppingconnect.aistudio.media.audio.Pcm
import com.shoppingconnect.aistudio.media.audio.SfxSynth
import com.shoppingconnect.aistudio.media.export.MediaExporter
import com.shoppingconnect.aistudio.media.shorts.AutoEditor
import com.shoppingconnect.aistudio.media.shorts.History
import com.shoppingconnect.aistudio.media.shorts.ShortsService
import com.shoppingconnect.aistudio.media.shorts.ShortsTemplates
import com.shoppingconnect.aistudio.media.shorts.TimelineOps
import com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter
import com.shoppingconnect.aistudio.media.subtitle.SrtIO
import com.shoppingconnect.aistudio.media.video.FrameRenderer
import com.shoppingconnect.aistudio.media.video.SceneMediaSource
import com.shoppingconnect.aistudio.pipeline.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** What is selected in the timeline / inspector. */
sealed interface Sel {
    data class SceneSel(val index: Int) : Sel
    data class TextSel(val id: String) : Sel
    data class SubSel(val id: String) : Sel
    data class SfxSel(val id: String) : Sel
    data class StickerSel(val id: String) : Sel
    data object Voice : Sel
    data object Music : Sel
}

data class StudioState(
    val loaded: Boolean = false,
    val timeline: ShortTimeline = ShortTimeline(),
    val shortformId: String? = null,
    val product: Product? = null,
    val isDemo: Boolean = false,
    val playheadMs: Long = 0,
    val playing: Boolean = false,
    val selection: Sel? = Sel.SceneSel(0),
    val zoom: Float = 1f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: String? = null,
    val message: String? = null,
    val recoverable: ShortTimeline? = null,
    val videoPath: String? = null,
    val thumbnail: ThumbnailSpec? = null,
)

@HiltViewModel
class StudioViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProjectRepository,
    private val media: MediaRepository,
    private val shorts: ShortsService,
    private val engines: AiEngineProvider,
    private val scheduler: WorkScheduler,
    private val settings: SettingsRepository,
    private val exporter: MediaExporter,
    private val files: ProjectFiles,
    renderDao: RenderJobDao,
) : ViewModel() {
    val projectId: String = checkNotNull(handle["projectId"])
    private val _s = MutableStateFlow(StudioState())
    val s: StateFlow<StudioState> = _s.asStateFlow()
    val assets: StateFlow<List<MediaAssetEntity>> = media.observe(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val renders: StateFlow<List<RenderJobEntity>> = renderDao.observeForProject(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame.asStateFlow()
    private var history = History(ShortTimeline())
    private var saveJob: Job? = null
    private var autosaveJob: Job? = null
    private var playJob: Job? = null
    private var frameJob: Job? = null
    private val previewMutex = Mutex()
    private var previewSource: SceneMediaSource? = null
    private var previewBitmap: Bitmap? = null
    private var player: android.media.MediaPlayer? = null
    private var previewAudio: File? = null
    private var previewAudioKey: Int = 0

    init {
        viewModelScope.launch {
            val b = repo.observeBundleOnce(projectId)
            val tl = b?.timeline ?: ShortTimeline()
            history = History(tl)
            _s.update { it.copy(loaded = true, timeline = tl, shortformId = b?.shortformId, product = b?.product, isDemo = b?.project?.isDemo == true, recoverable = b?.autosave?.takeIf { a -> a != tl }, videoPath = b?.videoPath, thumbnail = b?.thumbnail) }
            requestFrame()
        }
    }

    // ---- timeline editing ------------------------------------------------------------------

    private fun commit(next: ShortTimeline, push: Boolean = true) {
        val clamped = TimelineOps.clampClips(next)
        if (push) history.push(clamped) else history.replace(clamped)
        _s.update { it.copy(timeline = clamped, canUndo = history.canUndo, canRedo = history.canRedo, playheadMs = it.playheadMs.coerceAtMost((clamped.durationMs - 1).coerceAtLeast(0))) }
        scheduleSave(); requestFrame()
    }

    /** Scene timing changed → captions/subtitles/sfx re-timed from the template. */
    private fun commitRetimed(next: ShortTimeline) {
        val facts = _s.value.product?.let { KeywordHighlighter.factTokens(it) }.orEmpty()
        commit(AutoEditor.retime(next, ShortsTemplates.of(next.template), facts).let { r -> if (next.voiceClips.isEmpty()) r else r })
    }

    fun undo() { history.undo()?.let { t -> _s.update { it.copy(timeline = t, canUndo = history.canUndo, canRedo = history.canRedo) }; scheduleSave(); requestFrame() } }
    fun redo() { history.redo()?.let { t -> _s.update { it.copy(timeline = t, canUndo = history.canUndo, canRedo = history.canRedo) }; scheduleSave(); requestFrame() } }

    fun select(sel: Sel?) = _s.update { it.copy(selection = sel) }
    fun setZoom(z: Float) = _s.update { it.copy(zoom = z.coerceIn(0.25f, 8f)) }

    private fun selectedScene(): Int? = (_s.value.selection as? Sel.SceneSel)?.index

    fun split() {
        val t = _s.value.timeline
        val i = t.sceneAt(_s.value.playheadMs)
        val local = _s.value.playheadMs - t.sceneStarts()[i]
        commitRetimed(TimelineOps.split(t, i, local))
    }
    fun deleteSelected() {
        val t = _s.value.timeline
        when (val sel = _s.value.selection) {
            is Sel.SceneSel -> { commitRetimed(TimelineOps.delete(t, sel.index)); select(Sel.SceneSel((sel.index - 1).coerceAtLeast(0))) }
            is Sel.TextSel -> commit(TimelineOps.deleteText(t, sel.id))
            is Sel.SubSel -> commit(TimelineOps.deleteSubtitle(t, sel.id))
            is Sel.SfxSel -> commit(TimelineOps.deleteSfx(t, sel.id))
            is Sel.StickerSel -> commit(TimelineOps.deleteSticker(t, sel.id))
            Sel.Music -> commit(t.copy(music = null, bgmMood = com.shoppingconnect.aistudio.domain.model.BgmMood.NONE))
            Sel.Voice -> commit(t.copy(voiceClips = emptyList()))
            null -> Unit
        }
    }
    fun duplicate() { selectedScene()?.let { commitRetimed(TimelineOps.duplicate(_s.value.timeline, it)) } }
    fun moveScene(d: Int) { selectedScene()?.let { i -> commitRetimed(TimelineOps.move(_s.value.timeline, i, i + d)); select(Sel.SceneSel((i + d).coerceIn(0, _s.value.timeline.scenes.lastIndex))) } }
    fun updateScene(i: Int, retime: Boolean = false, f: (Scene) -> Scene) {
        val n = TimelineOps.updateScene(_s.value.timeline, i, f)
        if (retime) commitRetimed(n) else commit(n)
    }
    fun setSceneDuration(i: Int, ms: Long) = commitRetimed(TimelineOps.setDuration(_s.value.timeline, i, ms))
    fun trimStart(i: Int, d: Long) = commitRetimed(TimelineOps.trimStart(_s.value.timeline, i, d))
    fun replaceMedia(i: Int, a: MediaAssetEntity) = commit(TimelineOps.replaceMedia(_s.value.timeline, i, a.id, a.path, a.mimeType.startsWith("video/")))
    fun update(f: (ShortTimeline) -> ShortTimeline) = commit(f(_s.value.timeline))
    fun updateText(id: String, f: (com.shoppingconnect.aistudio.domain.model.TextClip) -> com.shoppingconnect.aistudio.domain.model.TextClip) = commit(TimelineOps.updateText(_s.value.timeline, id, f))
    fun updateSub(id: String, f: (com.shoppingconnect.aistudio.domain.model.SubtitleCue) -> com.shoppingconnect.aistudio.domain.model.SubtitleCue) = commit(TimelineOps.updateSubtitle(_s.value.timeline, id, f))
    fun addText() { val t = _s.value.timeline; val n = TimelineOps.addText(t, "텍스트", _s.value.playheadMs); commit(n); select(Sel.TextSel(n.texts.last().id)) }
    fun addSticker(e: String) { val n = TimelineOps.addSticker(_s.value.timeline, e, _s.value.playheadMs); commit(n); select(Sel.StickerSel(n.stickers.last().id)) }
    fun addSfx(type: SfxType) = commit(TimelineOps.addSfx(_s.value.timeline, AudioClip(startMs = _s.value.playheadMs, sfx = type, label = type.label, volume = 0.7f)))

    fun applyTemplate(id: ShortsTemplateId) {
        val tpl = ShortsTemplates.of(id)
        val t = _s.value.timeline
        val scenes = t.scenes.mapIndexed { i, s -> s.copy(transitionIn = s.transitionIn.copy(type = com.shoppingconnect.aistudio.media.shorts.SmartTransition.choose(tpl, i, s.purpose, null), durationMs = tpl.transitionMs), motion = tpl.motions[i % tpl.motions.size], backgroundArgb = tpl.backgroundArgb) }
        val cleared = t.copy(template = id, scenes = scenes, subtitleStyle = tpl.subtitleStyle, bgmMood = if (t.music == null) tpl.bgm else t.bgmMood, texts = t.texts.filter { it.sceneId == null })
        commitRetimed(cleared)
    }

    fun chooseHook(h: HookCandidate) {
        val t = _s.value.timeline
        if (t.scenes.isEmpty()) return
        val n = TimelineOps.updateScene(t.copy(hook = h.text), 0) { it.copy(narration = h.text, caption = h.text.take(18)) }
        commitRetimed(n)
        _s.update { it.copy(message = "Hook을 바꿨습니다. 음성을 다시 생성하면 내레이션에 반영됩니다.") }
    }

    fun importSrt(uri: Uri, ctx: android.content.Context) = viewModelScope.launch {
        runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty() }
            .mapCatching { SrtIO.parse(it) }
            .onSuccess { cues -> if (cues.isEmpty()) msg("SRT에서 자막을 찾지 못했습니다.") else commit(_s.value.timeline.copy(subtitles = cues, subtitlesEnabled = true)) }
            .onFailure { msg("SRT를 읽지 못했습니다.") }
    }

    fun exportSrt(): File { val f = File(files.shareDir, "subtitles.srt"); f.writeText(SrtIO.export(_s.value.timeline.subtitles)); return f }

    fun importMedia(uri: Uri, forScene: Int?) = viewModelScope.launch {
        runCatching { media.importUri(projectId, uri) }.onSuccess { a ->
            when {
                a.kind == AssetKind.AUDIO -> commit(_s.value.timeline.copy(music = AudioClip(path = a.path, label = "사용자 BGM"), bgmMood = com.shoppingconnect.aistudio.domain.model.BgmMood.NONE))
                forScene != null -> replaceMedia(forScene, a)
                else -> {
                    val t = _s.value.timeline
                    commitRetimed(t.copy(scenes = t.scenes + Scene(mediaAssetId = a.id, mediaPath = a.path, isVideo = a.mimeType.startsWith("video/"), durationMs = 3000, backgroundArgb = ShortsTemplates.of(t.template).backgroundArgb)))
                }
            }
        }.onFailure { e -> msg(e.toAppException().userMessage) }
    }

    // ---- AI & voice ------------------------------------------------------------------------

    private suspend fun engine() = if (_s.value.isDemo) DemoAiEngine() else engines.engine()

    /** "AI 자동 편집": regenerate the whole draft for a duration (AI rewrites the script for that length). */
    fun autoEdit(durationSec: Int = _s.value.timeline.targetDurationSec, template: ShortsTemplateId = _s.value.timeline.template) = viewModelScope.launch {
        val b = repo.observeBundleOnce(projectId) ?: return@launch
        val p = b.product ?: return@launch
        val a = b.article ?: com.shoppingconnect.aistudio.domain.model.Article()
        busy("AI가 스토리보드와 편집을 다시 만드는 중…")
        try {
            val r = shorts.createDraft(engine(), projectId, p, a, b.visualPlan?.cards.orEmpty(), settings.current(), template, durationSec, b.strategy?.usage ?: UsageStatus.INTRO_ONLY)
            commit(r.timeline)
            _s.update { it.copy(thumbnail = r.thumbnail) }
            repo.saveShortform(projectId, r.timeline, r.thumbnail, thumbnailPath = r.thumbnailAsset?.path)
            busy(null); if (r.warnings.isNotEmpty()) msg(r.warnings.joinToString("\n"))
        } catch (e: Exception) { busy(null); msg(e.toAppException().userMessage) }
    }

    fun regenerateVoice() = viewModelScope.launch {
        val p = _s.value.product ?: return@launch
        busy("음성을 합성하고 타임라인을 맞추는 중…")
        try { commit(shorts.synthesize(projectId, _s.value.timeline, p)); busy(null) }
        catch (e: Exception) { busy(null); msg(e.toAppException().userMessage) }
    }

    /** Shortens narration of every scene with AI when the voice made the video too long. */
    fun aiShorten() = viewModelScope.launch {
        val p = _s.value.product ?: return@launch
        busy("내레이션을 줄이는 중…")
        try {
            val e = engine()
            var t = _s.value.timeline
            t.scenes.forEachIndexed { i, sc ->
                if (sc.narration.length > 20) {
                    val shorter = e.rewrite(p, sc.narration, com.shoppingconnect.aistudio.ai.RewriteAction.SHORTEN, UsageStatus.INTRO_ONLY)
                    t = TimelineOps.updateScene(t, i) { it.copy(narration = shorter) }
                }
            }
            commit(t)
            busy(null); regenerateVoice()
        } catch (e: Exception) { busy(null); msg(e.toAppException().userMessage) }
    }

    fun hooks() = viewModelScope.launch {
        val p = _s.value.product ?: return@launch
        busy("Hook 후보를 만드는 중…")
        try { val h = engine().hooks(p); commit(_s.value.timeline.copy(hookCandidates = h)); busy(null) } catch (e: Exception) { busy(null); msg(e.toAppException().userMessage) }
    }

    // ---- preview & playback ----------------------------------------------------------------

    fun seek(ms: Long) { _s.update { it.copy(playheadMs = ms.coerceIn(0, (it.timeline.durationMs - 1).coerceAtLeast(0))) }; requestFrame(); if (_s.value.playing) player?.seekTo(ms.toInt()) }

    private fun requestFrame() {
        frameJob?.cancel()
        frameJob = viewModelScope.launch { renderFrame(_s.value.playheadMs) }
    }

    private suspend fun renderFrame(t: Long) = previewMutex.withLock {
        val tl = _s.value.timeline
        withContext(Dispatchers.Default) {
            val w = 360; val h = 640
            val src = previewSource ?: SceneMediaSource({ it.mediaPath?.takeIf { p -> files.isInside(p) } }, 720, 32 * 1024 * 1024, accurateVideoFrames = false).also { previewSource = it }
            // Double buffer: the bitmap handed to the UI is never drawn into again.
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            FrameRenderer(tl, w, h, src).draw(Canvas(bmp), t, preview = true)
            previewBitmap = bmp
            _frame.value = bmp
        }
    }

    fun togglePlay() { if (_s.value.playing) stop() else play() }

    private fun play() {
        _s.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            val t = _s.value.timeline
            val audio = runCatching { previewAudioFor(t) }.getOrNull()
            player?.release(); player = null
            if (audio != null) player = android.media.MediaPlayer().apply { setDataSource(audio.absolutePath); prepare(); seekTo(_s.value.playheadMs.toInt()); start() }
            val startWall = System.currentTimeMillis(); val startPos = _s.value.playheadMs
            while (isActive && _s.value.playing) {
                val pos = player?.currentPosition?.toLong() ?: (startPos + System.currentTimeMillis() - startWall)
                if (pos >= t.durationMs) { _s.update { it.copy(playheadMs = 0) }; stop(); break }
                _s.update { it.copy(playheadMs = pos) }
                renderFrame(pos)
                delay(40)
            }
        }
    }

    fun stop() { _s.update { it.copy(playing = false) }; playJob?.cancel(); player?.runCatching { stop(); release() }; player = null; requestFrame() }

    /** Mixed preview audio (same mixer as export), cached until the audio-relevant timeline changes. */
    private suspend fun previewAudioFor(t: ShortTimeline): File = withContext(Dispatchers.Default) {
        val key = listOf(t.voiceClips, t.music, t.bgmMood, t.sfx, t.mix, t.durationMs, t.scenes.map { it.mediaPath to it.volume }).hashCode()
        previewAudio?.takeIf { key == previewAudioKey && it.exists() }?.let { return@withContext it }
        val voice = t.voiceClips.mapNotNull { c -> c.path?.let { runCatching { PlacedAudio(AudioDecoder.decode(File(it)), c.startMs, c.volume) }.getOrNull() } }
        val music = t.music?.path?.let { runCatching { AudioDecoder.decode(File(it), t.durationMs + 1000) }.getOrNull() } ?: BgmSynth.generate(t.bgmMood, t.durationMs)
        val sfx = t.sfx.mapNotNull { c -> c.sfx?.let { PlacedAudio(SfxSynth.generate(it), c.startMs, c.volume) } }
        val pcm = AudioMixer.mix(t.durationMs, t.mix, voice, music, sfx, emptyList())
        val mono = FloatArray(pcm.size / 2) { (pcm[it * 2] + pcm[it * 2 + 1]) / 2f / 32768f }
        val f = File(files.workDir(projectId), "preview.wav")
        com.shoppingconnect.aistudio.media.audio.WavIO.write(f, Pcm(mono))
        previewAudio = f; previewAudioKey = key
        f
    }

    // ---- persistence, render, export --------------------------------------------------------

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1500); save() }
        // Crash-recovery autosave of the working copy.
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch { delay(400); _s.value.shortformId?.let { repo.autosaveShortform(it, _s.value.timeline) } }
    }

    private suspend fun save() {
        val id = repo.saveShortform(projectId, _s.value.timeline, _s.value.thumbnail)
        repo.autosaveShortform(id, null)
        _s.update { it.copy(shortformId = id) }
    }

    fun recover() { _s.value.recoverable?.let { commit(it) }; _s.update { it.copy(recoverable = null) } }
    fun discardRecovery() = viewModelScope.launch { _s.value.shortformId?.let { repo.autosaveShortform(it, null) }; _s.update { it.copy(recoverable = null) } }

    fun render(settingsOverride: RenderSettings) = viewModelScope.launch {
        commit(_s.value.timeline.copy(render = settingsOverride))
        save()
        val sid = _s.value.shortformId ?: return@launch
        scheduler.enqueueRender(projectId, sid, settingsOverride)
        msg("렌더링 대기열에 추가했습니다. 알림과 이 화면에서 진행률을 확인할 수 있습니다.")
    }

    fun cancelRender(jobId: String) = viewModelScope.launch { scheduler.cancelRender(jobId) }

    fun saveToGallery(path: String) = viewModelScope.launch {
        runCatching { exporter.saveVideo(File(path), "${_s.value.product?.title?.take(30) ?: "shorts"}.mp4") }
            .onSuccess { msg("Movies/AIStudio 에 저장했습니다.") }.onFailure { msg(it.toAppException().userMessage) }
    }

    fun shareIntent(path: String) = exporter.shareIntent(listOf(File(path)), "video/mp4", _s.value.product?.affiliateUrl, "숏폼 공유")

    fun updateThumbnail(spec: ThumbnailSpec) { _s.update { it.copy(thumbnail = spec) }; scheduleSave() }

    private fun busy(m: String?) = _s.update { it.copy(busy = m) }
    fun msg(m: String?) = _s.update { it.copy(message = m) }

    override fun onCleared() {
        player?.release(); previewSource?.release()
        if (saveJob?.isActive == true) kotlinx.coroutines.runBlocking { runCatching { save() }.onFailure { AppLog.w("Studio", "final save failed", it) } }
        super.onCleared()
    }
}
