package com.shoppingconnect.aistudio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shoppingconnect.aistudio.core.common.formatDate
import com.shoppingconnect.aistudio.data.db.ProjectEntity
import com.shoppingconnect.aistudio.domain.model.ProjectStatus
import com.shoppingconnect.aistudio.ui.theme.LocalExtraColors

@Composable
fun statusColor(s: ProjectStatus): Color {
    val extra = LocalExtraColors.current
    return when (s) {
        ProjectStatus.DRAFT -> MaterialTheme.colorScheme.outline
        ProjectStatus.GENERATED -> MaterialTheme.colorScheme.primary
        ProjectStatus.EDITED -> MaterialTheme.colorScheme.tertiary
        ProjectStatus.READY -> extra.warning
        ProjectStatus.PUBLISHED -> extra.success
        ProjectStatus.ARCHIVED -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun ProjectCard(p: ProjectEntity, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "프로젝트 ${p.title}, 상태 ${p.status.label}" },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileImage(p.thumbnailPath, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.title.ifBlank { "분석 중인 상품" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(p.status.label, statusColor(p.status))
                    if (p.isDemo) StatusChip("DEMO", MaterialTheme.colorScheme.tertiary)
                    if (p.hasBlog) Icon(Icons.AutoMirrored.Filled.Article, "블로그 있음", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    if (p.hasShorts) Icon(Icons.Default.Movie, "숏폼 있음", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Text(p.updatedAt.formatDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
