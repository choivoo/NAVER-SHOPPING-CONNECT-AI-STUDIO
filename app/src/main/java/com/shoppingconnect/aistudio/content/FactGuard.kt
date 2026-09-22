package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.core.common.formatPrice
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType
import com.shoppingconnect.aistudio.domain.model.ContentIssue
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.Severity

/**
 * QC agent (deterministic hallucination guard). SOURCE DATA vs GENERATED TEXT:
 * every price, discount rate, rating/review count and spec-like number in the generated
 * article must be traceable to the verified product data; otherwise it is flagged.
 */
object FactGuard {
    private val priceRegex = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\\s*원")
    private val manwonRegex = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*만\\s*원")
    private val percentRegex = Regex("([0-9]{1,3})\\s*%\\s*(?:할인|세일|off|OFF)?")
    private val reviewRegex = Regex("(?:리뷰|후기)\\s*([0-9,]+)\\s*(?:개|건)")
    private val ratingRegex = Regex("(?:평점|별점)\\s*([0-9](?:\\.[0-9])?)")

    fun check(article: Article, product: Product): List<ContentIssue> {
        val issues = mutableListOf<ContentIssue>()
        val sourceText = buildString {
            append(product.title).append(' ')
            product.description?.let { append(it).append(' ') }
            product.specifications.forEach { append(it.name).append(' ').append(it.value).append(' ') }
            product.options.forEach { append(it).append(' ') }
        }
        val allowedPrices = setOfNotNull(
            product.price.takeIf { product.isVerified(Product.F_PRICE) },
            product.originalPrice.takeIf { product.isVerified(Product.F_ORIGINAL_PRICE) },
        )
        val discount = product.discountRate

        article.blocks.filter { it.aiGenerated && it.type != BlockType.PRODUCT_CARD && it.type != BlockType.DISCLOSURE }.forEach { b ->
            val text = (listOf(b.text) + b.items).joinToString(" ")
            priceRegex.findAll(text).forEach { m ->
                val v = m.groupValues[1].replace(",", "").toLongOrNull()
                if (v != null && v !in allowedPrices && !sourceText.contains(m.groupValues[1])) {
                    issues += ContentIssue(
                        "PRICE_MISMATCH", Severity.ERROR,
                        "원본 데이터에 없는 가격 \"${m.value}\"",
                        allowedPrices.firstOrNull()?.let { "확인된 가격: ${formatPrice(it)}" } ?: "가격이 확인되지 않았습니다. 가격 언급을 삭제하세요.",
                        b.id, m.value,
                    )
                }
            }
            manwonRegex.findAll(text).forEach { m ->
                val v = m.groupValues[1].toDoubleOrNull()?.times(10000)?.toLong()
                if (v != null && allowedPrices.none { kotlin.math.abs(it - v) < 5000 }) {
                    issues += ContentIssue("PRICE_MISMATCH", Severity.WARNING, "확인되지 않은 가격 표현 \"${m.value}\"", "원본 가격과 비교해 수정하세요.", b.id, m.value)
                }
            }
            percentRegex.findAll(text).forEach { m ->
                val v = m.groupValues[1].toIntOrNull() ?: return@forEach
                val isDiscountCtx = m.value.contains("할인") || m.value.contains("세일") || m.value.contains("off", true) || text.contains("할인")
                if (isDiscountCtx && (discount == null || kotlin.math.abs(discount - v) > 1) && !sourceText.contains("$v%")) {
                    issues += ContentIssue("DISCOUNT_MISMATCH", Severity.ERROR, "원본과 다른 할인율 \"${m.value.trim()}\"", discount?.let { "확인된 할인율: 약 $it%" } ?: "할인 정보가 확인되지 않았습니다.", b.id, m.value)
                }
            }
            reviewRegex.find(text)?.let { m ->
                val v = m.groupValues[1].replace(",", "").toIntOrNull()
                if (v == null || product.reviewCount == null || v != product.reviewCount) {
                    issues += ContentIssue("REVIEW_COUNT", Severity.ERROR, "확인되지 않은 리뷰 수 \"${m.value}\"", "리뷰 수 언급을 삭제하거나 원본 값으로 수정하세요.", b.id, m.value)
                }
            }
            ratingRegex.find(text)?.let { m ->
                val v = m.groupValues[1].toDoubleOrNull()
                if (v == null || product.rating == null || kotlin.math.abs(v - product.rating) > 0.05) {
                    issues += ContentIssue("RATING", Severity.ERROR, "확인되지 않은 평점 \"${m.value}\"", "평점 언급을 삭제하거나 원본 값으로 수정하세요.", b.id, m.value)
                }
            }
        }
        val brand = product.brand
        if (brand != null && product.isVerified(Product.F_BRAND)) {
            val all = article.plainText()
            if (!all.contains(brand, ignoreCase = true) && !article.title.contains(brand, ignoreCase = true)) {
                issues += ContentIssue("BRAND_MISSING", Severity.INFO, "본문에 브랜드명(${brand})이 없습니다.", "상품 정보 요약에 브랜드가 자동 포함됩니다.")
            }
        }
        return issues
    }
}
