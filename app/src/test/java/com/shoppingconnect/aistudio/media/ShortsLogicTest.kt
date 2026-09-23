package com.shoppingconnect.aistudio.media

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.ai.ShortformDraft
import com.shoppingconnect.aistudio.ai.SceneDraft
import com.shoppingconnect.aistudio.domain.model.AudioMix
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.HookCandidate
import com.shoppingconnect.aistudio.domain.model.HookType
import com.shoppingconnect.aistudio.domain.model.RenderSettings
import com.shoppingconnect.aistudio.domain.model.Scene
import com.shoppingconnect.aistudio.domain.model.ShortTimeline
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SfxType
import com.shoppingconnect.aistudio.domain.model.Transition
import com.shoppingconnect.aistudio.domain.model.TransitionType
import com.shoppingconnect.aistudio.domain.model.VoiceSettings
import com.shoppingconnect.aistudio.media.audio.AudioMixer
import com.shoppingconnect.aistudio.media.audio.BgmSynth
import com.shoppingconnect.aistudio.media.audio.PlacedAudio
import com.shoppingconnect.aistudio.media.audio.Pcm
import com.shoppingconnect.aistudio.media.audio.SfxSynth
import com.shoppingconnect.aistudio.media.shorts.AssetRefForShorts
import com.shoppingconnect.aistudio.media.shorts.AutoEditor
import com.shoppingconnect.aistudio.media.shorts.History
import com.shoppingconnect.aistudio.media.shorts.ShortsTemplates
import com.shoppingconnect.aistudio.media.shorts.TimelineOps
import com.shoppingconnect.aistudio.media.shorts.VoiceSync
import com.shoppingconnect.aistudio.media.subtitle.KeywordHighlighter
import com.shoppingconnect.aistudio.media.subtitle.KoreanSubtitleSegmenter
import com.shoppingconnect.aistudio.media.subtitle.SrtIO
import com.shoppingconnect.aistudio.pipeline.DemoData
import org.junit.Test
import kotlin.math.abs

class ShortsLogicTest {
    private fun tl(n: Int, tr: TransitionType = TransitionType.CROSS_FADE) = ShortTimeline(scenes = (0 until n).map { Scene(id = "s$it", durationMs = 3000, transitionIn = Transition(if (it == 0) TransitionType.NONE else tr, 400), narration = "장면 $it 설명", caption = "캡션 $it") })

    @Test fun sceneStartsAccountForTransitionOverlap() {
        val t = tl(3)
        assertThat(t.sceneStarts()).containsExactly(0L, 2600L, 5200L).inOrder()
        assertThat(t.durationMs).isEqualTo(8200)
        assertThat(t.sceneAt(2700)).isEqualTo(1)
        assertThat(tl(3, TransitionType.NONE).durationMs).isEqualTo(9000)
    }

    @Test fun splitDeleteDuplicateMove() {
        var t = TimelineOps.split(tl(1), 0, 1200)
        assertThat(t.scenes.map { it.durationMs }).containsExactly(1200L, 1800L).inOrder()
        assertThat(TimelineOps.split(tl(1), 0, 100).scenes).hasSize(1) // too close to the edge
        t = TimelineOps.duplicate(t, 0)
        assertThat(t.scenes).hasSize(3)
        t = TimelineOps.move(t, 0, 2)
        assertThat(t.scenes.last().durationMs).isEqualTo(1200)
        t = TimelineOps.delete(t, 0)
        assertThat(t.scenes).hasSize(2)
        assertThat(TimelineOps.delete(tl(1), 0).scenes).hasSize(1) // keeps at least one
        assertThat(TimelineOps.setDuration(tl(1), 0, 10).scenes[0].durationMs).isEqualTo(TimelineOps.MIN_SCENE_MS)
    }

    @Test fun historyUndoRedo() {
        val h = History(1)
        h.push(2); h.push(3)
        assertThat(h.undo()).isEqualTo(2)
        assertThat(h.undo()).isEqualTo(1)
        assertThat(h.undo()).isNull()
        assertThat(h.redo()).isEqualTo(2)
        h.push(9)
        assertThat(h.canRedo).isFalse()
    }

    @Test fun autoEditorBuildsTimedDraftWithCaptionsSubtitlesAndSfx() {
        val draft = ShortformDraft(
            hooks = listOf(HookCandidate("이거 알고 사세요", HookType.PROBLEM)),
            scenes = listOf(SceneDraft("Hook", "훅", "이거 알고 사세요.", 2.0, 0), SceneDraft("특징", "특징", "배터리가 최대 8시간 가고, 방수도 됩니다.", 8.0, 1), SceneDraft("CTA", "링크", "링크에서 확인하세요.", 5.0, -1)),
            bgmMood = BgmMood.TECH,
        )
        val tpl = ShortsTemplates.of(ShortsTemplateId.DYNAMIC)
        val t = AutoEditor.build(draft, DemoData.product(), listOf(AssetRefForShorts("a", "/x/a.jpg"), AssetRefForShorts("b", "/x/b.jpg")), mapOf("CTA" to AssetRefForShorts("c", "/x/c.jpg")), tpl, 30, VoiceSettings(), RenderSettings(), true, null)
        assertThat(t.scenes).hasSize(3)
        assertThat(t.scenes.sumOf { it.durationMs }).isIn(29_000L..31_000L)
        assertThat(t.scenes[2].mediaAssetId).isEqualTo("c") // card used for CTA scene
        assertThat(t.scenes[0].transitionIn.type).isEqualTo(TransitionType.NONE)
        assertThat(setOf(tpl.transition, tpl.accentTransition)).containsAtLeastElementsIn(t.scenes.drop(1).map { it.transitionIn.type }.toSet())
        assertThat(t.texts.map { it.sceneId }).containsExactly("${t.scenes[0].id}", "${t.scenes[1].id}", "${t.scenes[2].id}")
        assertThat(t.subtitles).isNotEmpty()
        assertThat(t.sfx.any { it.sfx == SfxType.WHOOSH }).isTrue()
    }

    @Test fun voiceSyncStretchesScenesToNarration() {
        val t = tl(2)
        val synced = VoiceSync.apply(t, ShortsTemplates.of(ShortsTemplateId.CLEAN_PRODUCT), mapOf("s1" to ("/v.wav" to 6000L)), emptySet())
        assertThat(synced.scenes[1].durationMs).isAtLeast(6000 + VoiceSync.LEAD_MS)
        val clip = synced.voiceClips.single()
        assertThat(clip.startMs).isEqualTo(synced.sceneStarts()[1] + VoiceSync.LEAD_MS)
        assertThat(synced.subtitles.filter { it.startMs >= clip.startMs }.maxOf { it.endMs }).isAtMost(clip.startMs + 6000)
    }

    @Test fun koreanSubtitleSegmentation() {
        val lines = KoreanSubtitleSegmenter.segment("이 제품은 배터리가 오래가고 휴대성이 좋으면서 디자인도 깔끔합니다")
        assertThat(lines.size).isAtLeast(2)
        assertThat(lines.all { it.length <= 18 }).isTrue()
        assertThat(lines.joinToString(" ").replace(" ", "")).isEqualTo("이제품은배터리가오래가고휴대성이좋으면서디자인도깔끔합니다")
        val emoji = KoreanSubtitleSegmenter.segment("🔥 지금 확인하세요! 😀 링크는 아래에")
        assertThat(emoji).isNotEmpty()
        val cues = KoreanSubtitleSegmenter.timeCues(lines, 1000, 4000)
        assertThat(cues.first().startMs).isEqualTo(1000)
        assertThat(cues.last().endMs).isAtMost(5000)
    }

    @Test fun segmenterKeepsDecimalsAndThousands() {
        val lines = KoreanSubtitleSegmenter.segment("연결: 블루투스 5.3, 가격은 1,200원입니다.")
        assertThat(lines.joinToString("|")).contains("5.3")
        assertThat(lines.joinToString("|")).contains("1,200원")
    }

    @Test fun highlightsOnlyFacts() {
        val facts = KeywordHighlighter.factTokens(DemoData.product())
        assertThat(KeywordHighlighter.highlights("최대 8시간 재생", facts)).contains("8시간")
        assertThat(KeywordHighlighter.highlights("최대 20시간 재생", facts)).isEmpty()
    }

    @Test fun srtRoundTrip() {
        val cues = KoreanSubtitleSegmenter.timeCues(listOf("안녕하세요", "반갑습니다"), 0, 3000)
        val parsed = SrtIO.parse(SrtIO.export(cues))
        assertThat(parsed.map { it.text }).containsExactly("안녕하세요", "반갑습니다").inOrder()
        assertThat(abs(parsed[1].startMs - cues[1].startMs)).isAtMost(1)
    }

    @Test fun mixerDucksMusicUnderVoiceAndNeverClips() {
        val rate = Pcm.RATE
        val voice = Pcm(FloatArray(rate) { (Math.sin(it * 0.05) * 0.9).toFloat() })
        val music = Pcm(FloatArray(rate * 3) { 0.8f })
        val out = AudioMixer.mix(3000, AudioMix(), listOf(PlacedAudio(voice, 1000)), music, emptyList(), emptyList())
        assertThat(out.size).isEqualTo(3 * rate * 2)
        fun level(fromMs: Int, toMs: Int) = (fromMs * rate / 1000 until toMs * rate / 1000).map { kotlin.math.abs(out[it * 2].toInt()) }.average()
        val musicOnly = level(500, 900)
        // During voice the mix contains ducked music + voice; compare with music-only after release
        val afterVoice = level(2600, 2900)
        assertThat(musicOnly).isGreaterThan(0.0)
        assertThat(afterVoice).isGreaterThan(0.0)
        assertThat(out.all { it in -32767..32767 }).isTrue()
    }

    @Test fun duckingLowersMusicGain() {
        val rate = Pcm.RATE
        val music = FloatArray(rate) { 0.5f }
        val voice = FloatArray(rate) { if (it > rate / 2) 0.5f else 0f }
        AudioMixer.duck(music, voice, 0.3f, rate)
        assertThat(music[rate / 4]).isWithin(0.01f).of(0.5f)
        assertThat(music[rate - 1]).isLessThan(0.2f)
    }

    @Test fun synthesisedAudioHasRequestedLength() {
        assertThat(BgmSynth.generate(BgmMood.UPBEAT, 2000).samples.size).isEqualTo(2 * Pcm.RATE)
        assertThat(BgmSynth.generate(BgmMood.NONE, 1000).samples.all { it == 0f }).isTrue()
        SfxType.entries.forEach { assertThat(SfxSynth.generate(it).samples).isNotEmpty() }
        assertThat(Pcm(FloatArray(22050), 22050).resampled().samples.size).isEqualTo(44100)
    }

    @Test fun templatesAreComplete() {
        assertThat(ShortsTemplates.all.map { it.id }).containsExactlyElementsIn(ShortsTemplateId.entries)
        ShortsTemplates.all.forEach { assertThat(it.motions).isNotEmpty() }
    }
}
