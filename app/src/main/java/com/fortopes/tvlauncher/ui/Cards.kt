package com.fortopes.tvlauncher.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fortopes.tvlauncher.data.ContinueItem
import com.fortopes.tvlauncher.data.TvApp

/** Width of the 16:9 cards in every row: about five and a half fit across a TV. */
val CardWidth = 156.dp
private val CardShape = RoundedCornerShape(10.dp)

/**
 * The Projectivy-style card: a wide banner with rounded corners that grows a little and gets a white
 * ring when the remote is on it.
 */
@Composable
fun BannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = TvColors.Card,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(CardWidth).aspectRatio(16f / 9f),
        shape = CardDefaults.shape(shape = CardShape),
        colors = CardDefaults.colors(containerColor = container, focusedContainerColor = container),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = CardShape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
fun AppCard(
    app: TvApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    moving: Boolean = false,
) {
    BannerCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        container = if (app.banner == null) app.color.darken() else TvColors.Card,
    ) {
        if (moving) {
            // Arrows over the card while it's being moved.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).zIndex(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("◀   ▶", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        when {
            app.banner != null -> Image(
                bitmap = app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // No TV banner (a phone app): its icon and name on its own colour, like Projectivy does.
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                app.icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A card with a symbol and a name, for HDMI inputs and shortcuts. */
@Composable
fun GlyphCard(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    detail: String? = null,
    colors: List<Color>? = null,
) {
    BannerCard(onClick = onClick, modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        // Its own colours (a scene's sunrise, a light that's on), bottom left to top right.
                        colors != null -> Brush.linearGradient(colors, start = Offset(0f, Float.POSITIVE_INFINITY), end = Offset(Float.POSITIVE_INFINITY, 0f))
                        // "On" things (something playing) glow in the accent colours.
                        active -> Brush.linearGradient(listOf(TvColors.Purple, TvColors.Blue))
                        else -> Brush.linearGradient(listOf(Color(0xFF262838), Color(0xFF15161D)))
                    },
                ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A camera's latest picture, refreshed every [refreshMs]. */
@Composable
fun CameraCard(
    name: String,
    loadImage: suspend () -> ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    refreshMs: Long = 15_000,
) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name) {
        while (true) {
            loadImage()?.let { image = it }
            delay(refreshMs)
        }
    }
    BannerCard(onClick = onClick, modifier = modifier) {
        image?.let { Image(bitmap = it, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))),
        )
        Text(
            text = name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

/** A Continue watching card: the show's picture, a progress bar along the bottom, and the app it's in. */
@Composable
fun ContinueCard(item: ContinueItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BannerCard(onClick = onClick, modifier = modifier) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Shade the bottom so the title reads over any picture.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.sourceLabel,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        item.progress?.let { p ->
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(TvColors.Purple),
                )
            }
        }
    }
}

/** A Jellyfin Live TV channel: its logo, number and name, and what's on now. */
@Composable
fun ChannelCard(channel: com.fortopes.tvlauncher.data.LiveChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BannerCard(onClick = onClick, modifier = modifier) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF262838), Color(0xFF15161D)))))
        if (channel.logoUrl != null) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp),
            )
        } else {
            Icon(
                painter = painterResource(com.fortopes.tvlauncher.R.drawable.ic_tv),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).padding(bottom = 22.dp).size(30.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 6.dp)) {
            Text(
                text = listOf(channel.number, channel.name).filter { it.isNotBlank() }.joinToString("  "),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.nowShowing?.let {
                Text(text = it, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Color.darken(): Color = Color(red * 0.55f, green * 0.55f, blue * 0.55f, 1f)

/** A film or show from Seerr: its backdrop, title, and a tick when it's already in your library. */
@Composable
fun RecommendedCard(item: com.fortopes.tvlauncher.data.SeerrItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BannerCard(onClick = onClick, modifier = modifier) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            AsyncImage(model = it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Text(
            text = item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
        val badge = when {
            item.available -> "In library"
            item.requested -> "Requested"
            else -> null
        }
        if (badge != null) {
            Text(
                text = badge,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(if (item.available) Color(0xCC16A34A) else Color(0xCC7C3AED), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
