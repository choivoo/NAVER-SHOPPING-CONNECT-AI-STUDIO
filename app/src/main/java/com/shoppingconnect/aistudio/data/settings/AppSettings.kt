package com.shoppingconnect.aistudio.data.settings

import com.shoppingconnect.aistudio.domain.model.ArticleLength
import com.shoppingconnect.aistudio.domain.model.BgmMood
import com.shoppingconnect.aistudio.domain.model.CardStylePreset
import com.shoppingconnect.aistudio.domain.model.ContentPreset
import com.shoppingconnect.aistudio.domain.model.ExportProfile
import com.shoppingconnect.aistudio.domain.model.RenderQuality
import com.shoppingconnect.aistudio.domain.model.ShortsTemplateId
import com.shoppingconnect.aistudio.domain.model.SubtitleStyle
import com.shoppingconnect.aistudio.domain.model.Tone
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import com.shoppingconnect.aistudio.domain.model.VideoResolution
import com.shoppingconnect.aistudio.domain.model.VoicePreset
import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) { SYSTEM("시스템"), LIGHT("라이트"), DARK("다크") }

/** Settings → AI → 모델 */
enum class ModelChoice(val label: String) { AUTO_BEST("Auto Best"), OPUS("Opus"), SONNET("Sonnet"), CUSTOM("Custom API Model") }

/** Settings → AI → Quality (cost control) */
enum class AiQuality(val label: String, val description: String) {
    ECONOMY("Economy", "Sonnet 우선 — 비용 절감"),
    BALANCED("Balanced", "고난도 작업은 Opus, 단순 작업은 Sonnet"),
    BEST("Best", "모든 작업에 Opus 우선"),
}

enum class AiConnection(val label: String) { DIRECT("Claude API 키 직접 입력 (개발자 모드)"), PROXY("백엔드 프록시 (프로덕션 권장)") }

@Serializable
data class BrandProfile(
    val blogTone: Tone = Tone.FRIENDLY,
    val defaultCta: String = "자세한 정보와 현재 가격은 아래 링크에서 확인해 보세요.",
    val defaultHashtags: List<String> = emptyList(),
    val signature: String = "",
    val bannedPhrases: List<String> = emptyList(),
)

@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val reduceMotion: Boolean = false,
    val notificationsAsked: Boolean = false,

    // AI
    val modelChoice: ModelChoice = ModelChoice.AUTO_BEST,
    val customModel: String = "",
    val aiQuality: AiQuality = AiQuality.BALANCED,
    val aiConnection: AiConnection = AiConnection.DIRECT,
    val proxyBaseUrl: String = "",
    val demoMode: Boolean = false,
    val strictJsonSchema: Boolean = true,
    val refusalFallback: Boolean = true,
    val expertMode: Boolean = false,
    val maxOutputTokens: Int = 16000,
    val effortOverride: String = "",
    val promptVersion: String = "v1",

    // Content
    val defaultPreset: ContentPreset = ContentPreset.INFO,
    val defaultTone: Tone = Tone.FRIENDLY,
    val defaultLength: ArticleLength = ArticleLength.MEDIUM,
    val defaultUsage: UsageStatus = UsageStatus.INTRO_ONLY,
    val askUsageEachTime: Boolean = true,
    val disclosureText: String = DEFAULT_DISCLOSURE,
    val priceChangeNotice: Boolean = true,
    val brand: BrandProfile = BrandProfile(),
    val defaultCardStyle: CardStylePreset = CardStylePreset.CLEAN,
    val cardExportWidth: Int = 1080,

    // Shortform
    val shortsDurationSec: Int = 30,
    val shortsResolution: VideoResolution = VideoResolution.P1080,
    val shortsFps: Int = 30,
    val shortsQuality: RenderQuality = RenderQuality.BALANCED,
    val shortsHevc: Boolean = false,
    val voiceEnabled: Boolean = true,
    val voicePreset: VoicePreset = VoicePreset.NARRATION,
    val ttsVoiceName: String = "",
    val bgmMood: BgmMood = BgmMood.UPBEAT,
    val subtitlesEnabled: Boolean = true,
    val subtitleStyle: SubtitleStyle = SubtitleStyle.KOREAN_SHORTS,
    val shortsTemplate: ShortsTemplateId = ShortsTemplateId.CLEAN_PRODUCT,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "",
    val exportProfile: ExportProfile = ExportProfile.GENERIC,

    // NAVER
    val naverBlogId: String = "",
    val naverBlogWriteUrl: String = DEFAULT_BLOG_WRITE_URL,
    val naverNickname: String = "",
    val naverConnectedAt: Long = 0,
    val naverClientIdOverride: String = "",
    val naverRedirectUriOverride: String = "",
    val naverTokenExchangeUrlOverride: String = "",
) {
    companion object {
        const val DEFAULT_DISCLOSURE = "이 게시물에는 제휴 링크가 포함될 수 있으며, 구매 시 일정 수수료를 받을 수 있습니다."
        const val DEFAULT_BLOG_WRITE_URL = "https://blog.naver.com/GoBlogWrite.naver"
        const val PRICE_CHANGE_NOTICE = "※ 가격 및 혜택은 작성 시점 기준이며, 판매처 사정에 따라 변동될 수 있습니다."
    }
}
