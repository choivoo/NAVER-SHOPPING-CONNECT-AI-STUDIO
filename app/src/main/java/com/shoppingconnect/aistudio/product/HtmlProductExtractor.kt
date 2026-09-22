package com.shoppingconnect.aistudio.product

import com.shoppingconnect.aistudio.core.json.AppJson
import com.shoppingconnect.aistudio.domain.model.Product
import com.shoppingconnect.aistudio.domain.model.Spec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Raw facts found on a page, each tagged with the structured source it came from. */
data class ExtractedFacts(
    val title: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val description: String? = null,
    val price: Long? = null,
    val originalPrice: Long? = null,
    val currency: String? = null,
    val seller: String? = null,
    val images: List<String> = emptyList(),
    val specs: List<Spec> = emptyList(),
    val options: List<String> = emptyList(),
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val structured: Boolean = false,
    val loginWall: Boolean = false,
    val blocked: Boolean = false,
)

/**
 * Site-agnostic product extraction from public HTML, in priority order:
 *   1. schema.org Product JSON-LD  2. embedded app state (__PRELOADED_STATE__/__NEXT_DATA__)
 *   3. OpenGraph / product meta tags  4. itemprop microdata  5. spec tables  6. <title>.
 * The page text is treated as data only; nothing from it is ever executed or used as an instruction.
 */
object HtmlProductExtractor {

    fun extract(html: String, baseUrl: String): ExtractedFacts {
        val doc = Jsoup.parse(html, baseUrl)
        val text = doc.text()
        val loginWall = doc.select("form[action*=nid.naver.com], input[name=pw], #frmNIDLogin").isNotEmpty()
        val blocked = doc.select("#captcha, .captcha, [id*=captcha]").isNotEmpty() ||
            text.contains("비정상적인 접근") || text.contains("자동입력 방지") || text.contains("Access Denied", true)

        var f = ExtractedFacts(loginWall = loginWall, blocked = blocked)
        f = merge(f, fromJsonLd(doc))
        f = merge(f, fromEmbeddedState(doc))
        f = merge(f, fromMeta(doc))
        f = merge(f, fromMicrodata(doc))
        if (f.specs.isEmpty()) f = f.copy(specs = specTables(doc))
        if (f.title.isNullOrBlank()) f = f.copy(title = doc.title().substringBefore(" : ").substringBefore(" | ").trim().ifBlank { null })
        return f.copy(
            images = f.images.map { absolute(doc, it) }.filter { it.startsWith("https://") || it.startsWith("http://") }.distinct().take(12),
            title = f.title?.let { cleanText(it) }?.take(200),
            description = f.description?.let { cleanText(it) }?.take(4000),
        )
    }

    private fun absolute(doc: Document, u: String): String = if (u.startsWith("//")) "https:$u" else runCatching {
        java.net.URI(doc.location()).resolve(u).toString()
    }.getOrDefault(u)

    private fun cleanText(s: String) = Jsoup.parse(s).text().replace(Regex("\\s+"), " ").trim()

    private fun merge(a: ExtractedFacts, b: ExtractedFacts?): ExtractedFacts {
        if (b == null) return a
        return a.copy(
            title = a.title ?: b.title, brand = a.brand ?: b.brand, category = a.category ?: b.category,
            description = a.description ?: b.description, price = a.price ?: b.price, originalPrice = a.originalPrice ?: b.originalPrice,
            currency = a.currency ?: b.currency, seller = a.seller ?: b.seller,
            images = (a.images + b.images).distinct(), specs = a.specs.ifEmpty { b.specs }, options = a.options.ifEmpty { b.options },
            rating = a.rating ?: b.rating, reviewCount = a.reviewCount ?: b.reviewCount, structured = a.structured || b.structured,
        )
    }

    // ---- JSON-LD ---------------------------------------------------------------------------

    private fun fromJsonLd(doc: Document): ExtractedFacts? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val el = runCatching { AppJson.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val product = findProductNode(el) ?: continue
            return productNodeToFacts(product)
        }
        return null
    }

    private fun findProductNode(el: JsonElement): JsonObject? = when (el) {
        is JsonArray -> el.firstNotNullOfOrNull { findProductNode(it) }
        is JsonObject -> {
            val type = el["@type"]
            val isProduct = when (type) {
                is JsonPrimitive -> type.content.equals("Product", true) || type.content.equals("ProductGroup", true)
                is JsonArray -> type.any { (it as? JsonPrimitive)?.content.equals("Product", true) }
                else -> false
            }
            if (isProduct) el else (el["@graph"]?.let { findProductNode(it) } ?: el["mainEntity"]?.let { findProductNode(it) })
        }
        else -> null
    }

    private fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> (v["name"] as? JsonPrimitive)?.contentOrNull
        is JsonArray -> v.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull ?: ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    private fun productNodeToFacts(p: JsonObject): ExtractedFacts {
        val images = when (val img = p["image"]) {
            is JsonPrimitive -> listOfNotNull(img.contentOrNull)
            is JsonArray -> img.mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull }
            is JsonObject -> listOfNotNull((img["url"] as? JsonPrimitive)?.contentOrNull)
            else -> emptyList()
        }
        val offers = when (val o = p["offers"]) {
            is JsonArray -> o.firstOrNull() as? JsonObject
            is JsonObject -> o
            else -> null
        }
        val price = offers?.let { parsePrice(it.str("price") ?: it.str("lowPrice")) }
        val high = offers?.let { parsePrice(it.str("highPrice")) }
        val rating = p["aggregateRating"] as? JsonObject
        val specs = (p["additionalProperty"] as? JsonArray)?.mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            val n = o.str("name"); val v = o.str("value")
            if (n != null && v != null) Spec(n, v) else null
        }.orEmpty()
        return ExtractedFacts(
            title = p.str("name"), brand = p.str("brand"), category = p.str("category"), description = p.str("description"),
            price = price, originalPrice = high?.takeIf { price != null && it > price }, currency = offers?.str("priceCurrency"),
            seller = offers?.str("seller"), images = images, specs = specs,
            rating = rating?.str("ratingValue")?.toDoubleOrNull(), reviewCount = rating?.str("reviewCount")?.toIntOrNull() ?: rating?.str("ratingCount")?.toIntOrNull(),
            structured = true,
        )
    }

    // ---- Embedded app state (Naver smartstore / shopping pages) ----------------------------

    private fun fromEmbeddedState(doc: Document): ExtractedFacts? {
        val candidates = mutableListOf<String>()
        doc.select("script#__NEXT_DATA__").forEach { candidates += it.data() }
        doc.select("script").forEach { s ->
            val d = s.data()
            val idx = d.indexOf("__PRELOADED_STATE__")
            if (idx >= 0) {
                val start = d.indexOf('{', idx)
                if (start > 0) candidates += d.substring(start).trim().trimEnd(';')
            }
        }
        for (c in candidates) {
            val el = runCatching { AppJson.parseToJsonElement(c) }.getOrNull() ?: continue
            val facts = deepFindProduct(el, 0) ?: continue
            return facts
        }
        return null
    }

    private val nameKeys = listOf("productName", "name", "dispName")
    private val priceKeys = listOf("discountedSalePrice", "mobileDiscountedSalePrice", "lowestPrice", "salePrice", "lowPrice", "price")
    private val originalPriceKeys = listOf("salePrice", "originalPrice", "listPrice")
    private val imageKeys = listOf("representativeImageUrl", "imageUrl", "representImageUrl", "url")

    /** Looks for an object that has both a product-ish name and a price. */
    private fun deepFindProduct(el: JsonElement, depth: Int): ExtractedFacts? {
        if (depth > 12) return null
        when (el) {
            is JsonObject -> {
                val name = nameKeys.firstNotNullOfOrNull { (el[it] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.length in 4..200 } }
                val price = priceKeys.firstNotNullOfOrNull { k -> (el[k] as? JsonPrimitive)?.contentOrNull?.let { parsePrice(it) } }
                if (name != null && price != null && price > 0) {
                    val orig = originalPriceKeys.firstNotNullOfOrNull { k -> (el[k] as? JsonPrimitive)?.contentOrNull?.let { parsePrice(it) } }
                        ?.takeIf { it > price }
                    val images = mutableListOf<String>()
                    collectImages(el, images, 0)
                    return ExtractedFacts(
                        title = name,
                        brand = ((el["naverShoppingSearchInfo"] as? JsonObject)?.get("brandName") as? JsonPrimitive)?.contentOrNull
                            ?: (el["brandName"] as? JsonPrimitive)?.contentOrNull,
                        category = ((el["category"] as? JsonObject)?.get("wholeCategoryName") as? JsonPrimitive)?.contentOrNull,
                        price = price, originalPrice = orig, currency = "KRW",
                        seller = ((el["channel"] as? JsonObject)?.get("channelName") as? JsonPrimitive)?.contentOrNull,
                        images = images, structured = true,
                    )
                }
                for (v in el.values) deepFindProduct(v, depth + 1)?.let { return it }
            }
            is JsonArray -> for (v in el.take(50)) deepFindProduct(v, depth + 1)?.let { return it }
            else -> Unit
        }
        return null
    }

    private fun collectImages(el: JsonElement, out: MutableList<String>, depth: Int) {
        if (depth > 4 || out.size >= 10) return
        when (el) {
            is JsonObject -> el.forEach { (k, v) ->
                if (v is JsonPrimitive && (k in imageKeys || k.contains("image", true)) && v.contentOrNull?.matches(Regex("https?://.+\\.(jpg|jpeg|png|webp)(\\?.*)?", RegexOption.IGNORE_CASE)) == true) out += v.content
                else collectImages(v, out, depth + 1)
            }
            is JsonArray -> el.take(12).forEach { collectImages(it, out, depth + 1) }
            else -> Unit
        }
    }

    // ---- OpenGraph / meta ------------------------------------------------------------------

    private fun meta(doc: Document, vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
        doc.selectFirst("meta[property=$k], meta[name=$k]")?.attr("content")?.takeIf { it.isNotBlank() }
    }

    private fun fromMeta(doc: Document): ExtractedFacts {
        val price = parsePrice(meta(doc, "product:price:amount", "og:price:amount", "product:sale_price:amount"))
        val orig = parsePrice(meta(doc, "product:original_price:amount"))
        return ExtractedFacts(
            title = meta(doc, "og:title", "twitter:title"),
            description = meta(doc, "og:description", "description", "twitter:description"),
            brand = meta(doc, "product:brand", "og:brand"),
            price = price, originalPrice = orig?.takeIf { price != null && it > price },
            currency = meta(doc, "product:price:currency", "og:price:currency"),
            images = doc.select("meta[property=og:image], meta[property=og:image:url], meta[name=twitter:image]").map { it.attr("content") }.filter { it.isNotBlank() },
            structured = price != null,
        )
    }

    private fun fromMicrodata(doc: Document): ExtractedFacts? {
        val scope = doc.selectFirst("[itemtype*=schema.org/Product]") ?: return null
        fun prop(n: String) = scope.selectFirst("[itemprop=$n]")?.let { it.attr("content").ifBlank { it.text() } }?.takeIf { it.isNotBlank() }
        return ExtractedFacts(
            title = prop("name"), brand = prop("brand"), description = prop("description"),
            price = parsePrice(prop("price")), currency = prop("priceCurrency"),
            images = scope.select("[itemprop=image]").mapNotNull { (it.attr("src").ifBlank { it.attr("content") }).takeIf { s -> s.isNotBlank() } },
            structured = true,
        )
    }

    private fun specTables(doc: Document): List<Spec> {
        val out = mutableListOf<Spec>()
        doc.select("table").filter { t ->
            val cls = (t.className() + " " + (t.parent()?.className() ?: "")).lowercase()
            cls.contains("spec") || cls.contains("detail") || cls.contains("attribute") || cls.contains("info")
        }.take(3).forEach { t ->
            t.select("tr").forEach { tr ->
                val th = tr.selectFirst("th")?.text()?.trim()
                val td = tr.selectFirst("td")?.text()?.trim()
                if (!th.isNullOrBlank() && !td.isNullOrBlank() && th.length < 40 && td.length < 200) out += Spec(th, td)
            }
        }
        return out.distinctBy { it.name }.take(15)
    }

    fun parsePrice(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val digits = s.replace(Regex("[^0-9.]"), "")
        if (digits.isBlank()) return null
        return digits.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
    }

    /** Converts extracted facts into a normalised [Product] with explicit verified/uncertain fields. */
    fun toProduct(
        id: String, facts: ExtractedFacts, sourceUrl: String, affiliateUrl: String,
        source: com.shoppingconnect.aistudio.domain.model.ProductSource, redirects: List<String>,
    ): Product {
        val verified = buildList {
            if (!facts.title.isNullOrBlank()) add(Product.F_TITLE)
            if (!facts.brand.isNullOrBlank()) add(Product.F_BRAND)
            if (!facts.category.isNullOrBlank()) add(Product.F_CATEGORY)
            if (!facts.description.isNullOrBlank()) add(Product.F_DESCRIPTION)
            if (facts.price != null) add(Product.F_PRICE)
            if (facts.originalPrice != null) add(Product.F_ORIGINAL_PRICE)
            if (!facts.seller.isNullOrBlank()) add(Product.F_SELLER)
            if (facts.images.isNotEmpty()) add(Product.F_IMAGES)
            if (facts.specs.isNotEmpty()) add(Product.F_SPECS)
            if (facts.options.isNotEmpty()) add(Product.F_OPTIONS)
            if (facts.rating != null) add(Product.F_RATING)
            if (facts.reviewCount != null) add(Product.F_REVIEW_COUNT)
        }
        return Product(
            id = id, sourceUrl = sourceUrl, affiliateUrl = affiliateUrl, title = facts.title.orEmpty(),
            brand = facts.brand, category = facts.category, description = facts.description,
            price = facts.price, originalPrice = facts.originalPrice, currency = facts.currency?.uppercase() ?: "KRW",
            seller = facts.seller, images = facts.images, specifications = facts.specs, options = facts.options,
            source = source, verifiedFields = verified, uncertainFields = Product.ALL_FIELDS.filterNot { it in verified },
            rating = facts.rating, reviewCount = facts.reviewCount, redirectChain = redirects,
        )
    }
}
