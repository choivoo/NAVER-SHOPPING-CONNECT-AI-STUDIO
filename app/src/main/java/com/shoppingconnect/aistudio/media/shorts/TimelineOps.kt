package com.shoppingconnect.aistudio.media.shorts

import com.shoppingconnect.aistudio.core.common.newId
import com.shoppingconnect.aistudio.domain.model.AudioClip
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.StickerClip
import com.shoppingconnect.aistudio.domain.model.SubtitleCue
import com.shoppingconnect.aistudio.domain.model.TextClip

/** Pure, testable timeline edit operations (the editor's undo/redo stores whole snapshots). */
object TimelineOps {
    const val MIN_SCENE_MS = 400L
    const val MAX_SCENE_MS = 60_000L

    fun split(t: ShortTimeline, sceneIndex: Int, atLocalMs: Long): ShortTimeline {
        val s = t.scenes.getOrNull(sceneIndex) ?: return t
        if (atLocalMs < MIN_SCENE_MS || s.durationMs - atLocalMs < MIN_SCENE_MS) return t
        val first = s.copy(durationMs = atLocalMs)
        val second = s.copy(
            id = newId(), durationMs = s.durationMs - atLocalMs, narration = "", caption = "",
            trimStartMs = s.trimStartMs + (atLocalMs * s.speed).toLong(),
            transitionIn = s.transitionIn.copy(type = com.shoppingconnect.aistudio.domain.model.TransitionType.NONE),
        )
        return t.copy(scenes = t.scenes.toMutableList().apply { set(sceneIndex, first); add(sceneIndex + 1, second) })
    }

    fun delete(t: ShortTimeline, sceneIndex: Int): ShortTimeline {
        if (t.scenes.size <= 1 || sceneIndex !in t.scenes.indices) return t
        val id = t.scenes[sceneIndex].id
        return t.copy(
            scenes = t.scenes.filterIndexed { i, _ -> i != sceneIndex },
            voiceClips = t.voiceClips.filter { it.sceneId != id },
            texts = t.texts.filter { it.sceneId != id },
        )
    }

    fun duplicate(t: ShortTimeline, sceneIndex: Int): ShortTimeline {
        val s = t.scenes.getOrNull(sceneIndex) ?: return t
        return t.copy(scenes = t.scenes.toMutableList().apply { add(sceneIndex + 1, s.copy(id = newId())) })
    }

    fun move(t: ShortTimeline, from: Int, to: Int): ShortTimeline {
        if (from !in t.scenes.indices || to !in t.scenes.indices || from == to) return t
        val list = t.scenes.toMutableList()
        val s = list.removeAt(from)
        list.add(to, s)
        return t.copy(scenes = list)
    }

    fun setDuration(t: ShortTimeline, sceneIndex: Int, ms: Long): ShortTimeline = updateScene(t, sceneIndex) { it.copy(durationMs = ms.coerceIn(MIN_SCENE_MS, MAX_SCENE_MS)) }

    fun trimStart(t: ShortTimeline, sceneIndex: Int, deltaMs: Long): ShortTimeline = updateScene(t, sceneIndex) { s ->
        val d = deltaMs.coerceIn(-s.trimStartMs, s.durationMs - MIN_SCENE_MS)
        s.copy(trimStartMs = s.trimStartMs + d, durationMs = s.durationMs - d)
    }

    fun trimEnd(t: ShortTimeline, sceneIndex: Int, deltaMs: Long): ShortTimeline = updateScene(t, sceneIndex) { s ->
        s.copy(durationMs = (s.durationMs + deltaMs).coerceIn(MIN_SCENE_MS, MAX_SCENE_MS))
    }

    fun replaceMedia(t: ShortTimeline, sceneIndex: Int, assetId: String, path: String, isVideo: Boolean): ShortTimeline =
        updateScene(t, sceneIndex) { it.copy(mediaAssetId = assetId, mediaPath = path, isVideo = isVideo, trimStartMs = 0) }

    fun updateScene(t: ShortTimeline, i: Int, f: (Scene) -> Scene): ShortTimeline {
        if (i !in t.scenes.indices) return t
        return t.copy(scenes = t.scenes.toMutableList().apply { set(i, f(get(i))) })
    }

    fun addText(t: ShortTimeline, text: String, atMs: Long, lengthMs: Long = 2500): ShortTimeline =
        t.copy(texts = t.texts + TextClip(text = text, startMs = atMs, endMs = (atMs + lengthMs).coerceAtMost(t.durationMs), yFraction = 0.45f))

    fun updateText(t: ShortTimeline, id: String, f: (TextClip) -> TextClip) = t.copy(texts = t.texts.map { if (it.id == id) f(it) else it })
    fun deleteText(t: ShortTimeline, id: String) = t.copy(texts = t.texts.filterNot { it.id == id })

    fun updateSubtitle(t: ShortTimeline, id: String, f: (SubtitleCue) -> SubtitleCue) = t.copy(subtitles = t.subtitles.map { if (it.id == id) f(it) else it })
    fun deleteSubtitle(t: ShortTimeline, id: String) = t.copy(subtitles = t.subtitles.filterNot { it.id == id })

    fun addSticker(t: ShortTimeline, emoji: String, atMs: Long) = t.copy(stickers = t.stickers + StickerClip(emoji = emoji, startMs = atMs, endMs = (atMs + 2000).coerceAtMost(t.durationMs)))
    fun deleteSticker(t: ShortTimeline, id: String) = t.copy(stickers = t.stickers.filterNot { it.id == id })

    fun addSfx(t: ShortTimeline, clip: AudioClip) = t.copy(sfx = t.sfx + clip)
    fun deleteSfx(t: ShortTimeline, id: String) = t.copy(sfx = t.sfx.filterNot { it.id == id })

    /** Keeps free clips inside the (possibly shorter) timeline. */
    fun clampClips(t: ShortTimeline): ShortTimeline {
        val d = t.durationMs
        return t.copy(
            texts = t.texts.filter { it.startMs < d }.map { it.copy(endMs = it.endMs.coerceAtMost(d)) },
            subtitles = t.subtitles.filter { it.startMs < d }.map { it.copy(endMs = it.endMs.coerceAtMost(d)) },
            stickers = t.stickers.filter { it.startMs < d }.map { it.copy(endMs = it.endMs.coerceAtMost(d)) },
            sfx = t.sfx.filter { it.startMs < d },
        )
    }
}

/** Bounded undo/redo history of immutable snapshots. */
class History<T>(initial: T, private val limit: Int = 60) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    var current: T = initial
        private set

    fun push(next: T) {
        if (next == current) return
        undo.addLast(current)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
        current = next
    }

    fun replace(next: T) { current = next }

    fun undo(): T? { val p = undo.removeLastOrNull() ?: return null; redo.addLast(current); current = p; return p }
    fun redo(): T? { val n = redo.removeLastOrNull() ?: return null; undo.addLast(current); current = n; return n }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
}
