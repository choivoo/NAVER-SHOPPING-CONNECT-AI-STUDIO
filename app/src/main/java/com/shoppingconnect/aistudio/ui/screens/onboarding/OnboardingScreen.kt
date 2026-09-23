package com.shoppingconnect.aistudio.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoppingconnect.aistudio.data.settings.SettingsRepository
import com.shoppingconnect.aistudio.ui.components.Dot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun finish(done: () -> Unit) = viewModelScope.launch { settings.update { it.copy(onboardingDone = true) }; done() }
}

private data class Page(val icon: ImageVector, val title: String, val body: String)

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pages = listOf(
        Page(Icons.Default.AutoAwesome, "NAVER Shopping Connect AI Studio", "상품 링크 하나로 블로그와 숏폼까지."),
        Page(Icons.Default.Movie, "상품 분석 → 블로그 → 이미지 → 숏폼", "AI 에이전트가 상품을 분석하고, 글·이미지 카드·9:16 숏폼 초안을 만듭니다. 확인되지 않은 정보는 추측하지 않습니다."),
        Page(Icons.Default.Link, "네이버 계정 연결", "공식 네이버 로그인으로 연결합니다. 앱은 비밀번호를 보거나 저장하지 않으며, 게시는 네이버 블로그 편집 화면에서 직접 확인 후 진행합니다."),
        Page(Icons.Default.Key, "AI 연결", "설정 → AI에서 Claude API 키(개발자 모드) 또는 백엔드 프록시를 연결하세요. 키는 Android Keystore로 암호화 저장됩니다. 키 없이 데모로 체험할 수도 있습니다."),
        Page(Icons.Default.CheckCircle, "준비 완료", "홈에서 링크를 붙여넣고 [AI 콘텐츠 만들기]를 눌러 시작하세요."),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row { Spacer(Modifier.weight(1f)); if (pager.currentPage < pages.lastIndex) TextButton(onClick = { vm.finish(onDone) }) { Text("건너뛰기") } }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Icon(p.icon, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(p.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { i -> Box(Modifier.padding(4.dp)) { Dot(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, 8) } }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (pager.currentPage == pages.lastIndex) vm.finish(onDone) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (pager.currentPage == pages.lastIndex) Text("시작하기") else { Text("다음"); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
        }
    }
}
