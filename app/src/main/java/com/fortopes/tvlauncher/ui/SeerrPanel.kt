package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fortopes.tvlauncher.data.SeerrItem

/** A film or show's details from the Recommended row: trailer, play or request. */
@Composable
fun SeerrPanel(
    item: SeerrItem,
    canPlay: Boolean,
    onTrailer: () -> Unit,
    onPlay: () -> Unit,
    onRequest: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(item.key) { runCatching { first.requestFocus() } }
    SidePanel(width = 460.dp) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            )
        }
        Text(item.title, color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        val facts = listOfNotNull(
            item.kind,
            item.year.ifBlank { null },
            item.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
            item.statusLabel.ifBlank { null },
        )
        Text(facts.joinToString(" · "), color = TvColors.Muted, fontSize = 14.sp)
        if (item.overview.isNotBlank()) {
            Text(item.overview, color = TvColors.Text.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 6.dp))
        }
        ListItem(selected = false, onClick = onTrailer, modifier = Modifier.focusRequester(first), headlineContent = { Text("Watch trailer") },
            supportingContent = { Text("Plays in YouTube") })
        when {
            item.available && canPlay -> ListItem(selected = false, onClick = onPlay, headlineContent = { Text("Play in Jellyfin") })
            item.available -> {}
            item.requested -> ListItem(selected = false, onClick = {}, enabled = false, headlineContent = { Text(item.statusLabel) })
            else -> ListItem(selected = false, onClick = onRequest, headlineContent = { Text("Request") },
                supportingContent = { Text("Ask Seerr to add it to Jellyfin") })
        }
    }
}
