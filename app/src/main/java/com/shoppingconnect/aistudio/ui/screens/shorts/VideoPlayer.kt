package com.shoppingconnect.aistudio.ui.screens.shorts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/** Media3 ExoPlayer for rendered MP4s (works offline). */
@Composable
fun VideoPlayer(path: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path)))); prepare() }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = modifier)
}
