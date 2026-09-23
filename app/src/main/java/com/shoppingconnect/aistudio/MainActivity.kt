package com.shoppingconnect.aistudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoppingconnect.aistudio.auth.NaverAuthManager
import com.shoppingconnect.aistudio.ui.AppEvents
import com.shoppingconnect.aistudio.ui.AppRoot
import com.shoppingconnect.aistudio.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var auth: NaverAuthManager
    @Inject lateinit var events: AppEvents
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ready.value }
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()
            if (ready) AppRoot(settings)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Returning from the Custom Tab without completing login.
        lifecycleScope.launch { kotlinx.coroutines.delay(1500); auth.cancelPending() }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = intent.data
        when {
            auth.isCallback(data) -> lifecycleScope.launch { auth.handleCallback(data!!) }
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { events.shareLink(it) }
            intent.hasExtra(EXTRA_PROJECT_ID) -> intent.getStringExtra(EXTRA_PROJECT_ID)?.let { events.openProject(it) }
        }
    }

    companion object { const val EXTRA_PROJECT_ID = "project_id" }
}
