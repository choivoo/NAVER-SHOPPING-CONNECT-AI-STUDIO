package com.shoppingconnect.aistudio.media.shorts

import com.shoppingconnect.aistudio.ai.ShortformDraft
import com.shoppingconnect.aistudio.domain.model.AudioClip
import com.shoppingconnect.aistudio.domain.model.AudioMix
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.StickerClip
import com.shoppingconnect.aistudio.domain.model.TextClip
import com.shoppingconnect.aistudio.domain.model.Transition
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter
import com.shoppingconnect.aistudio.media.subtitle.KoreanSubtitleSegmenter

data class AssetRefForShorts(val id: String, val path: String)

/**
 * "AI 자동 편집": storyboard → scenes → assets → motion → text → subtitles → sfx → music → transitions.
 * Voice timing is applied afterwards by [VoiceSync] once real TTS durations are known.
 */
object AutoEditor {
    fun build(
        draft: ShortformDraft,
        product: Product,
        productImages: List<AssetRefForShorts>,
        cardImages: Map<String, AssetRefForShorts>,
        template: ShortsTemplate,
        targetSec: Int,
        voice: VoiceSettings,
        render: RenderSettings,
        subtitlesEnabled: Boolean,
        watermark: String?,
        selectedHook: HookCandidate? = draft.hooks.firstOrNull(),
    ): ShortTimeline {
        val drafts = draft.scenes.ifEmpty { return ShortTimeline() }
        val totalDraft = drafts.sumOf { it.durationSec }.takeIf { it > 0 } ?: 1.0
        val scale = targetSec / totalDraft
        val scenes = drafts.mapIndexed { i, d ->
            val img = productImages.getOrNull(d.imageIndex)
            val card = if (img == null) cardFor(d.purpose, cardImages) else null
            val media = img ?: card ?: productImages.getOrNull(i % maxOf(productImages.size, 1))
            Scene(
                purpose = d.purpose,
                mediaAssetId = media?.id,
                mediaPath = media?.path,
                durationMs = (d.durationSec * scale * 1000).toLong().coerceIn(1200, 15_000),
                narration = if (i == 0 && selectedHook != null) selectedHook.text else d.narration,
                caption = if (i == 0 && selectedHook != null) selectedHook.text.take(18) else d.onScreenText,
                transitionIn = Transition(SmartTransition.choose(template, i, d.purpose, d.transition), template.transitionMs),
                motion = if (card != null && img == null) template.motions.first() else d.motion.takeIf { it in template.motions } ?: template.motions[i % template.motions.size],
                fit = if (card != null && img == null) com.shoppingconnect.aistudio.domain.model.FitMode.FIT else template.fit,
                backgroundArgb = template.backgroundArgb,
            )
        }
        val base = ShortTimeline(
            template = template.id,
            targetDurationSec = targetSec,
            scenes = scenes,
            subtitleStyle = template.subtitleStyle,
            subtitlesEnabled = subtitlesEnabled,
            bgmMood = if (draft.bgmMood == template.bgm || template.bgm == com.shoppingconnect.aistudio.domain.model.BgmMood.NONE) draft.bgmMood else template.bgm,
            voice = voice,
            mix = AudioMix(),
            render = render,
            hook = selectedHook?.text.orEmpty(),
            hookCandidates = draft.hooks,
            watermark = watermark,
        )
        return retime(base, template, KeywordHighlighter.factTokens(product))
    }

    private fun cardFor(purpose: String, cards: Map<String, AssetRefForShorts>): AssetRefForShorts? {
        val p = purpose.lowercase()
        return when {
            p.contains("cta") || p.contains("링크") -> cards["CTA"] ?: cards["HERO"]
            p.contains("대상") || p.contains("추천") || p.contains("target") -> cards["TARGET"] ?: cards["HERO"]
            p.contains("확인") || p.contains("check") -> cards["CHECK"]
            p.contains("특징") || p.contains("feature") -> cards["FEATURE"] ?: cards["HERO"]
            else -> cards["HERO"]
        }
    }

    /** Recomputes scene captions, provisional subtitles and sfx from the current scene timing. Free text clips are kept. */
    fun retime(t: ShortTimeline, template: ShortsTemplate, facts: Set<String> = emptySet()): ShortTimeline {
        val starts = t.sceneStarts()
        val free = t.texts.filter { it.sceneId == null }
        val sceneTexts = t.scenes.mapIndexedNotNull { i, s ->
            if (s.caption.isBlank()) return@mapIndexedNotNull null
            val prev = t.texts.firstOrNull { it.sceneId == s.id }
            TextClip(
                id = prev?.id ?: com.shoppingconnect.aistudio.core.common.newId(),
                text = s.caption, startMs = starts[i] + 120, endMs = starts[i] + s.durationMs - 120,
                style = prev?.style ?: template.textStyle, enter = prev?.enter ?: template.textEnter, exit = prev?.exit ?: template.textExit,
                yFraction = prev?.yFraction ?: template.textY, sizeSp = prev?.sizeSp ?: 64f,
                highlight = facts.firstOrNull { s.caption.replace(" ", "").contains(it) },
                sceneId = s.id,
            )
        }
        val texts = sceneTexts + free
        val subtitles = t.scenes.flatMapIndexed { i, s ->
            val voice = t.voiceClips.firstOrNull { it.sceneId == s.id }
            val start = voice?.startMs ?: (starts[i] + 150)
            val dur = voice?.durationMs?.takeIf { it > 0 } ?: (s.durationMs - 300)
            KoreanSubtitleSegmenter.timeCues(KoreanSubtitleSegmenter.segment(s.narration), start, dur)
                .map { it.copy(highlights = com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter.highlights(it.text, facts)) }
        }
        val sfx = buildList {
            template.hookSfx?.let { add(AudioClip(startMs = 80, sfx = it, label = it.label, volume = 0.8f)) }
            template.transitionSfx?.let { type ->
                t.scenes.forEachIndexed { i, s -> if (i > 0 && s.transitionIn.type != TransitionType.NONE) add(AudioClip(startMs = starts[i], sfx = type, label = type.label, volume = 0.6f)) }
            }
        }
        val stickers = template.hookSticker?.let { listOf(StickerClip(emoji = it, startMs = 200, endMs = t.scenes.firstOrNull()?.durationMs ?: 2000)) } ?: t.stickers
        return t.copy(texts = texts, subtitles = subtitles, sfx = sfx, stickers = stickers)
    }

}

/** Voice Sync Engine: script → TTS → measured durations → timeline recalculation. */
object VoiceSync {
    const val LEAD_MS = 150L
    const val TAIL_MS = 250L

    fun apply(t: ShortTimeline, template: ShortsTemplate, voiceBySceneId: Map<String, Pair<String, Long>>, facts: Set<String>): ShortTimeline {
        val scenes = t.scenes.map { s ->
            val v = voiceBySceneId[s.id] ?: return@map s
            val needed = LEAD_MS + v.second + TAIL_MS + s.transitionIn.clampedMs / 2
            // Scene grows to fit the narration; shrinks toward it (never below 1.2 s).
            s.copy(durationMs = maxOf(needed, 1200L).coerceAtMost(20_000))
        }
        val timed = t.copy(scenes = scenes)
        val starts = timed.sceneStarts()
        val clips = scenes.mapIndexedNotNull { i, s ->
            val v = voiceBySceneId[s.id] ?: return@mapIndexedNotNull null
            AudioClip(path = v.first, startMs = starts[i] + LEAD_MS, durationMs = v.second, sceneId = s.id, label = "Voice ${i + 1}", volume = 1f)
        }
        return AutoEditor.retime(timed.copy(voiceClips = clips), template, facts)
    }

    /** True when narration made the video noticeably longer than the target (UI offers AI shortening). */
    fun overTarget(t: ShortTimeline): Boolean = t.durationMs > t.targetDurationSec * 1000L * 1.2
}
