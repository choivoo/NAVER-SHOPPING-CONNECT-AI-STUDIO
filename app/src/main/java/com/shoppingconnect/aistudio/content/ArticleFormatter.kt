package com.shoppingconnect.aistudio.content

import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType

/** Exports for the Naver Blog hand-off: rich HTML (keeps formatting when pasted) and plain text. */
object ArticleFormatter {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun inline(s: String) = esc(s).replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")

    fun toHtml(article: Article, imageMarker: (String) -> String? = { null }): String = buildString {
        append("<h1>").append(esc(article.title)).append("</h1>\n")
        article.blocks.forEach { b ->
            when (b.type) {
                BlockType.H1 -> append("<h1>${inline(b.text)}</h1>\n")
                BlockType.H2 -> append("<h2>${inline(b.text)}</h2>\n")
                BlockType.PARAGRAPH -> append("<p>${inline(b.text)}</p>\n")
                BlockType.QUOTE -> append("<blockquote>${inline(b.text)}</blockquote>\n")
                BlockType.LIST -> { append("<ul>"); b.items.forEach { append("<li>${inline(it)}</li>") }; append("</ul>\n") }
                BlockType.IMAGE -> append("<p>[${esc(b.assetId?.let(imageMarker) ?: "이미지")}]</p>\n")
                BlockType.DIVIDER -> append("<hr/>\n")
                BlockType.LINK -> append("<p><a href=\"${esc(b.url.orEmpty())}\">${esc(b.text.ifBlank { b.url.orEmpty() })}</a></p>\n")
                BlockType.PRODUCT_CARD -> {
                    append("<table border=\"1\" cellpadding=\"6\"><tr><th colspan=\"2\">${esc(b.text)}</th></tr>")
                    b.items.forEach { row ->
                        val k = row.substringBefore(":", ""); val v = row.substringAfter(": ", row)
                        if (k.isNotBlank() && row.contains(":")) append("<tr><td><b>${esc(k)}</b></td><td>${esc(v)}</td></tr>") else append("<tr><td colspan=\"2\">${esc(row)}</td></tr>")
                    }
                    append("</table>\n")
                }
                BlockType.CTA -> append("<p><b>${inline(b.text)}</b>${b.url?.let { " <a href=\"${esc(it)}\">바로가기</a>" }.orEmpty()}</p>\n")
                BlockType.DISCLOSURE -> append("<p><small>${esc(b.text)}</small></p>\n")
            }
        }
        if (article.hashtags.isNotEmpty()) append("<p>${article.hashtags.joinToString(" ") { "#" + esc(it) }}</p>\n")
    }

    fun toPlainText(article: Article): String = buildString {
        appendLine(article.title).appendLine()
        article.blocks.forEach { b ->
            when (b.type) {
                BlockType.H1, BlockType.H2 -> appendLine("■ ${b.text}")
                BlockType.PARAGRAPH, BlockType.QUOTE, BlockType.DISCLOSURE -> appendLine(b.text.replace("**", ""))
                BlockType.LIST -> b.items.forEach { appendLine("• ${it.replace("**", "")}") }
                BlockType.IMAGE -> appendLine("[이미지]")
                BlockType.DIVIDER -> appendLine("────────")
                BlockType.LINK -> appendLine("${b.text}: ${b.url}")
                BlockType.PRODUCT_CARD -> { appendLine("[${b.text}]"); b.items.forEach { appendLine("- $it") } }
                BlockType.CTA -> appendLine("${b.text}${b.url?.let { " $it" }.orEmpty()}")
            }
            appendLine()
        }
        if (article.hashtags.isNotEmpty()) appendLine(article.hashtags.joinToString(" ") { "#$it" })
    }.trim()
}
