package com.shoppingconnect.aistudio.content

import com.google.common.truth.Truth.assertThat
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.Block
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.domain.model.Spec
import com.shoppingconnect.aistudio.domain.model.UsageStatus
import org.junit.Test

class ContentGuardsTest {
    private val product = Product(
        id = "p", sourceUrl = "https://x.com/p", affiliateUrl = "https://naver.me/a", title = "무선 청소기 V9", brand = "클린랩",
        price = 199000, originalPrice = 249000, specifications = listOf(Spec("배터리", "최대 60분")), reviewCount = 120, rating = 4.6,
        verifiedFields = listOf(Product.F_TITLE, Product.F_BRAND, Product.F_PRICE, Product.F_ORIGINAL_PRICE, Product.F_SPECS, Product.F_RATING, Product.F_REVIEW_COUNT),
    )

    private fun article(vararg texts: String, disclosure: Boolean = true) = Article(
        title = "무선 청소기 V9 특징 정리",
        blocks = texts.map { Block(type = BlockType.PARAGRAPH, text = it) } + if (disclosure) listOf(Block(type = BlockType.DISCLOSURE, text = "제휴 링크 포함", aiGenerated = false)) else emptyList(),
    )

    @Test fun flagsFakeExperienceOnlyForNonUsers() {
        val a = article("제가 직접 써보니 흡입력이 좋았어요.")
        assertThat(ComplianceChecker.check(a, UsageStatus.NOT_USED, true).map { it.code }).contains("FAKE_EXPERIENCE")
        assertThat(ComplianceChecker.check(a, UsageStatus.USED, true).map { it.code }).doesNotContain("FAKE_EXPERIENCE")
        assertThat(ComplianceChecker.neutralize("며칠 사용해봤는데 좋아요")).doesNotContain("며칠 사용해")
    }

    @Test fun flagsExaggerationAndMissingDisclosure() {
        val issues = ComplianceChecker.check(article("무조건 사야 하는 최고의 청소기", disclosure = false), UsageStatus.INTRO_ONLY, true)
        assertThat(issues.map { it.code }).containsAtLeast("EXAGGERATION", "MISSING_DISCLOSURE")
        assertThat(issues.first { it.code == "EXAGGERATION" }.suggestion).isNotNull()
    }

    @Test fun factGuardAcceptsSourcePricesAndRejectsInventedOnes() {
        assertThat(FactGuard.check(article("판매가는 199,000원 입니다. 정가는 249,000원."), product).filter { it.severity != Severity.INFO }).isEmpty()
        val bad = FactGuard.check(article("지금 159,000원에 50% 할인! 리뷰 3,000개, 평점 4.9"), product)
        assertThat(bad.map { it.code }).containsAtLeast("PRICE_MISMATCH", "DISCOUNT_MISMATCH", "REVIEW_COUNT", "RATING")
        assertThat(bad.filter { it.severity != Severity.INFO }.all { it.severity == Severity.ERROR }).isTrue()
        assertThat(FactGuard.check(article("클린랩 제품 20% 할인 중"), product)).isEmpty() // 249000→199000 ≈ 20%
    }

    @Test fun factGuardIgnoresAppGeneratedBlocks() {
        val a = Article(blocks = listOf(Block(type = BlockType.PRODUCT_CARD, items = listOf("판매가: 1원"), aiGenerated = false)))
        assertThat(FactGuard.check(a, product).filter { it.severity != Severity.INFO }).isEmpty()
    }

    @Test fun qualityScoreIsExplainedAndPenalisesStuffing() {
        val good = Article(
            title = "무선 청소기 V9 특징과 구매 전 확인할 점",
            blocks = listOf(Block(type = BlockType.DISCLOSURE, text = "광고")) + (1..4).flatMap { listOf(Block(type = BlockType.H2, text = "소제목 $it"), Block(type = BlockType.PARAGRAPH, text = "무선 청소기 V9 의 특징을 정리했습니다. " + "가벼운 무게와 긴 사용 시간이 장점입니다. ".repeat(3))) } +
                listOf(Block(type = BlockType.PRODUCT_CARD, items = listOf("a")), Block(type = BlockType.LINK, url = "https://x")),
            hashtags = listOf("청소기", "무선청소기", "V9"),
        )
        val r = SeoAnalyzer.analyze(good, "무선 청소기 V9", 3)
        assertThat(r.score).isAtLeast(80)
        val stuffed = good.copy(blocks = good.blocks + Block(type = BlockType.PARAGRAPH, text = "무선 청소기 V9 ".repeat(60)))
        assertThat(SeoAnalyzer.analyze(stuffed, "무선 청소기 V9", 3).checks.first { it.name == "키워드 과다 사용 없음" }.passed).isFalse()
    }

    @Test fun splitsLongParagraphsAtSentences() {
        val long = "첫 문장입니다. ".repeat(40)
        val parts = KoreanText.splitParagraph(long, 120)
        assertThat(parts.size).isGreaterThan(1)
        assertThat(parts.all { it.length <= 130 }).isTrue()
    }

    @Test fun duplicateDetectorAndContentMemory() {
        val a = article("이 제품은 가볍고 조용해서 매일 쓰기 좋습니다. 배터리도 오래 갑니다.")
        val same = ContentMemory.mostSimilar(a, listOf("이전 글" to a))
        assertThat(ContentMemory.duplicateIssue(same)).isNotNull()
        val other = article("완전히 다른 주방용품 이야기를 담은 글이며 공통 표현이 거의 없습니다.")
        assertThat(ContentMemory.duplicateIssue(ContentMemory.mostSimilar(a, listOf("x" to other)))).isNull()
        val phrases = ContentMemory.frequentPhrases(listOf(a, a.copy()))
        assertThat(phrases).isNotEmpty()
    }

    @Test fun formatterEscapesHtmlAndKeepsLinks() {
        val a = Article(title = "<b>제목</b>", blocks = listOf(Block(type = BlockType.PARAGRAPH, text = "**굵게** <script>"), Block(type = BlockType.LINK, text = "보기", url = "https://naver.me/a")), hashtags = listOf("태그"))
        val html = ArticleFormatter.toHtml(a)
        assertThat(html).contains("<b>굵게</b>")
        assertThat(html).contains("&lt;script&gt;")
        assertThat(html).contains("href=\"https://naver.me/a\"")
        assertThat(ArticleFormatter.toPlainText(a)).contains("#태그")
    }
}
