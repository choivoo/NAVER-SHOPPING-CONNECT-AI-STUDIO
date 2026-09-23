package com.shoppingconnect.aistudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shoppingconnect.aistudio.data.db.MediaAssetEntity
import com.shoppingconnect.aistudio.domain.model.Article
import com.shoppingconnect.aistudio.domain.model.BlockType

fun boldMarkup(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val s = rest.indexOf("**"); if (s < 0) { append(rest); break }
        val e = rest.indexOf("**", s + 2); if (e < 0) { append(rest); break }
        append(rest.substring(0, s))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(rest.substring(s + 2, e)); pop()
        rest = rest.substring(e + 2)
    }
}

/** Read-only blog rendering, styled like a mobile blog post (used by Preview and Publish). */
@Composable
fun BlogRenderer(article: Article, assets: List<MediaAssetEntity>, scale: Float = 1f, modifier: Modifier = Modifier) {
    val fg = Color(0xFF1E1E1E)
    Column(modifier.background(Color.White).padding(horizontal = (20 * scale).dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(article.title, color = fg, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold, lineHeight = (32 * scale).sp)
        HorizontalDivider(color = Color(0xFFE5E5E5))
        article.blocks.forEach { b ->
            when (b.type) {
                BlockType.H1 -> Text(boldMarkup(b.text), color = fg, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                BlockType.H2 -> Text(boldMarkup(b.text), color = fg, fontSize = (19 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                BlockType.PARAGRAPH -> Text(boldMarkup(b.text), color = fg, fontSize = (16 * scale).sp, lineHeight = (27 * scale).sp)
                BlockType.QUOTE -> Row { Box(Modifier.width(3.dp).background(Color(0xFF9E9E9E))); Text(boldMarkup(b.text), color = Color(0xFF555555), fontSize = (16 * scale).sp, modifier = Modifier.padding(start = 12.dp)) }
                BlockType.LIST -> Column { b.items.forEach { Text(boldMarkup("• $it"), color = fg, fontSize = (16 * scale).sp, lineHeight = (26 * scale).sp) } }
                BlockType.IMAGE -> FileImage(assets.firstOrNull { it.id == b.assetId }?.path, Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)), crop = false)
                BlockType.DIVIDER -> HorizontalDivider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 8.dp))
                BlockType.LINK -> Text(b.text.ifBlank { b.url.orEmpty() }, color = Color(0xFF1A73E8), textDecoration = TextDecoration.Underline, fontSize = (16 * scale).sp)
                BlockType.PRODUCT_CARD -> Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.text, color = fg, fontWeight = FontWeight.Bold)
                    b.items.forEach { Text(it, color = Color(0xFF424242), fontSize = (14 * scale).sp) }
                }
                BlockType.CTA -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF3F1FF)).padding(14.dp)) { Text(boldMarkup(b.text), color = Color(0xFF3B2FC9), fontWeight = FontWeight.SemiBold) }
                BlockType.DISCLOSURE -> Text(b.text, color = Color(0xFF757575), fontSize = (13 * scale).sp, modifier = Modifier.background(Color(0xFFF5F5F5)).padding(10.dp).fillMaxWidth())
            }
        }
        if (article.hashtags.isNotEmpty()) Text(article.hashtags.joinToString(" ") { "#$it" }, color = Color(0xFF1A73E8), fontSize = (14 * scale).sp)
    }
}
