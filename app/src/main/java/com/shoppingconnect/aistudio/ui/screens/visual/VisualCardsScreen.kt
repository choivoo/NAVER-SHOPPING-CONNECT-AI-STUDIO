package com.shoppingconnect.aistudio.ui.screens.visual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.domain.model.CardType
import com.shoppingconnect.aistudio.ui.components.AppTopBar
import com.shoppingconnect.aistudio.ui.components.FileImage
import com.shoppingconnect.aistudio.ui.components.SectionCard

@Composable
fun VisualCardsScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: VisualCardsViewModel = hiltViewModel()) {
    val b by vm.bundle.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("블로그 이미지 카드", onBack) { TextButton(onClick = vm::replan, enabled = !busy) { Icon(Icons.Default.AutoAwesome, null); Text("AI 재기획") } } }) { pad ->
        val cards = b?.visualPlan?.cards.orEmpty()
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyVerticalGrid(GridCells.Adaptive(220.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(title = "카드 추가 · 내보내기") {
                        Text("카드는 앱이 그린 정보 그래픽입니다. 실물 사진처럼 보이게 만들지 않으며, 정보 카드의 수치는 검증된 원본 데이터만 사용합니다.", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CardType.entries.filter { it != CardType.COMPARISON }.forEach { t -> AssistChip(onClick = { vm.add(t) }, label = { Text("+ ${t.label}") }, enabled = !busy) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(1080, 1440, 2160).forEach { w -> OutlinedButton(onClick = { vm.exportAll(w) }, enabled = !busy && cards.isNotEmpty()) { Text("${w}px 저장") } } }
                    }
                }
                items(cards, key = { it.id }) { c ->
                    Card {
                        FileImage(b?.assets?.firstOrNull { it.id == c.renderedAssetId }?.path, Modifier.fillMaxWidth().aspectRatio(c.style.aspect.w.toFloat() / c.style.aspect.h), contentDescription = c.title, crop = false)
                        Column(Modifier.padding(8.dp)) {
                            Text("${c.type.label} · ${c.style.preset.label} · ${c.style.aspect.label}", style = MaterialTheme.typography.labelMedium)
                            Row {
                                IconButton(onClick = { onEdit(c.id) }) { Icon(Icons.Default.Edit, "편집") }
                                IconButton(onClick = { vm.regenerate(c.id) }, enabled = !busy) { Icon(Icons.Default.Refresh, "다시 생성") }
                                IconButton(onClick = { vm.move(c.id, -1) }, enabled = !busy) { Icon(Icons.Default.ArrowBack, "앞으로 이동") }
                                IconButton(onClick = { vm.move(c.id, 1) }, enabled = !busy) { Icon(Icons.Default.ArrowForward, "뒤로 이동") }
                                IconButton(onClick = { vm.delete(c.id) }, enabled = !busy) { Icon(Icons.Default.Delete, "삭제") }
                            }
                        }
                    }
                }
            }
        }
    }
    msg?.let { AlertDialog(onDismissRequest = vm::dismiss, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::dismiss) { Text("확인") } }) }
}
