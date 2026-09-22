package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.Severity
import com.shoppingconnect.aistudio.domain.model.UsageStatus

/** Rule-based compliance: fake experience, exaggeration (Content Guard), disclosure presence. */
object ComplianceChecker {
    /** Phrases that claim first-hand use. Forbidden when the creator did not use the product. */
    val FAKE_EXPERIENCE_PATTERNS = listOf(
        "직접 써보니", "직접 써 보니", "직접 사용해보니", "직접 사용해 보니", "직접 구매해서", "직접 구매해", "며칠 사용해", "며칠 써",
        "써보니까", "써 보니까", "사용해보니", "사용해 보니", "실사용 후기", "내돈내산", "제가 써본", "제가 사용해본", "한 달 사용", "일주일 사용",
        "직접 써봤", "직접 사용해봤", "구매해서 써봤", "사용 후기",
    )

    data class Exaggeration(val term: String, val suggestion: String)

    val EXAGGERATIONS = listOf(
        Exaggeration("무조건", "\"대체로\", \"많은 경우\"처럼 조건을 둔 표현"),
        Exaggeration("100%", "근거가 없다면 수치를 빼고 \"충분히\" 등으로"),
        Exaggeration("최고", "\"만족도가 높은 편\", \"눈에 띄는\""),
        Exaggeration("절대", "\"거의\", \"쉽게 ~하지 않는\""),
        Exaggeration("완벽", "\"꼼꼼한\", \"잘 갖춘\""),
        Exaggeration("1위", "공식 출처가 있으면 출처와 기준 시점을 함께 표기"),
        Exaggeration("최저가", "\"작성 시점 기준 가격\""),
        Exaggeration("역대급", "\"눈에 띄는\""),
        Exaggeration("부작용 없", "효능·안전 관련 단정 표현은 삭제 권장"),
        Exaggeration("의사 추천", "근거 없는 권위 인용 삭제"),
    )

    fun check(article: Article, usage: UsageStatus, disclosureRequired: Boolean): List<ContentIssue> {
        val issues = mutableListOf<ContentIssue>()
        for (b in article.blocks) {
            val texts = if (b.type == BlockType.LIST) b.items else listOf(b.text)
            for (t in texts) {
                if (usage != UsageStatus.USED && b.type != BlockType.DISCLOSURE) {
                    FAKE_EXPERIENCE_PATTERNS.firstOrNull { t.contains(it) }?.let { p ->
                        issues += ContentIssue(
                            "FAKE_EXPERIENCE", Severity.ERROR,
                            "실제로 사용하지 않은 제품에 사용 경험 표현(\"$p\")이 있습니다.",
                            "\"공개된 상품 정보를 기준으로 보면\" 같은 표현으로 바꿔 주세요.", b.id, t.take(80),
                        )
                    }
                }
                EXAGGERATIONS.filter { t.contains(it.term) }.forEach { e ->
                    issues += ContentIssue("EXAGGERATION", Severity.WARNING, "근거 없는 단정/과장 표현 \"${e.term}\"", "수정 추천: ${e.suggestion}", b.id, t.take(80))
                }
            }
        }
        if (usage != UsageStatus.USED && FAKE_EXPERIENCE_PATTERNS.any { article.title.contains(it) || article.title.contains("후기") }) {
            issues += ContentIssue("FAKE_EXPERIENCE", Severity.ERROR, "제목에 사용 후기 표현이 있습니다.", "\"정리\", \"정보\" 등으로 바꿔 주세요.", excerpt = article.title)
        }
        EXAGGERATIONS.filter { article.title.contains(it.term) }.forEach { e ->
            issues += ContentIssue("EXAGGERATION", Severity.WARNING, "제목에 과장 표현 \"${e.term}\"", "수정 추천: ${e.suggestion}", excerpt = article.title)
        }
        if (disclosureRequired && article.blocks.none { it.type == BlockType.DISCLOSURE && it.text.isNotBlank() }) {
            issues += ContentIssue("MISSING_DISCLOSURE", Severity.ERROR, "제휴/광고 표시 문구가 없습니다.", "편집기 툴바의 [Disclosure]로 고지 문구를 추가하세요.")
        }
        return issues
    }

    /** Rewrites fake-experience phrases into neutral information-based phrasing. */
    fun neutralize(text: String): String {
        var out = text
        FAKE_EXPERIENCE_PATTERNS.forEach { out = out.replace(it, "제품 정보를 기준으로 보면") }
        return out
    }
}
