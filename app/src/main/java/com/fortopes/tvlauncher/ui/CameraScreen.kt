package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fortopes.tvlauncher.data.HaEntity
import kotlinx.coroutines.delay

/** A camera full screen, with a fresh picture every second. Left and right change camera; Back closes. */
@Composable
fun CameraScreen(cameras: List<HaEntity>, start: Int, loadImage: suspend (String) -> ImageBitmap?) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, (cameras.size - 1).coerceAtLeast(0))) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val focus = remember { FocusRequester() }
    val camera = cameras.getOrNull(index)

    LaunchedEffect(camera?.id) {
        image = null
        val id = camera?.id ?: return@LaunchedEffect
        while (true) {
            loadImage(id)?.let { image = it }
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || cameras.isEmpty()) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { index = (index - 1 + cameras.size) % cameras.size; true }
                    Key.DirectionRight -> { index = (index + 1) % cameras.size; true }
                    else -> false
                }
            },
    ) {
        image?.let { Image(bitmap = it, contentDescription = camera?.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        Text(
            text = camera?.name?.let { "$it  ·  ${index + 1} of ${cameras.size}" } ?: "No cameras",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(32.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
