package com.red.sovereign.calls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/**
 * 📱 Unified Participant Video Grid — RED Sovereign 2026
 *
 * Consolidated, high-performance video tile grid for:
 * • Group Calls (Mesh & SFU)
 * • Conferences & Audio Spaces
 * • Zoom-Style Meetings
 *
 * Supports dynamic 1..12 tiles grid, active speaker highlighting,
 * spotlight/pinned view, and glassmorphic user tags.
 */

data class VideoGridTile(
    val id: String,
    val label: String,
    val videoTrack: VideoTrack?,
    val isLocal: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false
)

@Composable
fun ParticipantVideoGrid(
    tiles: List<VideoGridTile>,
    eglContext: EglBase.Context?,
    pinnedTileId: String? = null,
    onPinTile: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (tiles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A)))
        return
    }

    val pinnedTile = tiles.firstOrNull { it.id == pinnedTileId }

    if (pinnedTile != null && tiles.size > 1) {
        // Spotlight / Pinned Mode Layout
        Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
            // Main Pinned Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                SingleVideoTile(
                    tile = pinnedTile,
                    eglContext = eglContext,
                    isPinned = true,
                    onClick = { onPinTile?.invoke(null) }
                )
            }

            // Thumbnail Strip for Other Participants
            val otherTiles = tiles.filter { it.id != pinnedTile.id }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                items(otherTiles, key = { it.id }) { tile ->
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                    ) {
                        SingleVideoTile(
                            tile = tile,
                            eglContext = eglContext,
                            isPinned = false,
                            onClick = { onPinTile?.invoke(tile.id) }
                        )
                    }
                }
            }
        }
    } else {
        // Dynamic Grid Mode Layout
        val total = tiles.size
        when (total) {
            1 -> {
                Box(modifier = modifier.fillMaxSize().padding(8.dp)) {
                    SingleVideoTile(tiles[0], eglContext, onClick = { onPinTile?.invoke(tiles[0].id) })
                }
            }
            2 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = modifier.fillMaxSize().padding(8.dp)
                ) {
                    tiles.forEach { tile ->
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SingleVideoTile(tile, eglContext, onClick = { onPinTile?.invoke(tile.id) })
                        }
                    }
                }
            }
            in 3..4 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = modifier.fillMaxSize().padding(8.dp)
                ) {
                    tiles.chunked(2).forEach { rowTiles ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            rowTiles.forEach { tile ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    SingleVideoTile(tile, eglContext, onClick = { onPinTile?.invoke(tile.id) })
                                }
                            }
                            if (rowTiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            in 5..6 -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = modifier.fillMaxSize().padding(6.dp)
                ) {
                    tiles.chunked(2).forEach { rowTiles ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            rowTiles.forEach { tile ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    SingleVideoTile(tile, eglContext, onClick = { onPinTile?.invoke(tile.id) })
                                }
                            }
                            if (rowTiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            else -> {
                // 7..12 tiles (3 columns grid)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = modifier.fillMaxSize().padding(6.dp)
                ) {
                    tiles.take(12).chunked(3).forEach { rowTiles ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            rowTiles.forEach { tile ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    SingleVideoTile(tile, eglContext, onClick = { onPinTile?.invoke(tile.id) })
                                }
                            }
                            repeat(3 - rowTiles.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleVideoTile(
    tile: VideoGridTile,
    eglContext: EglBase.Context?,
    isPinned: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (tile.isSpeaking) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SpeakerGlow"
    )

    val borderModifier = if (tile.isSpeaking) {
        Modifier.border(
            width = 2.5.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF00FF88).copy(alpha = glowAlpha),
                    Color(0xFF00C98C).copy(alpha = glowAlpha)
                )
            ),
            shape = RoundedCornerShape(16.dp)
        )
    } else {
        Modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
            .then(borderModifier)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        // Video Track or Avatar Fallback
        if (tile.videoTrack != null && eglContext != null) {
            WebrtcVideo(
                track = tile.videoTrack,
                egl = eglContext,
                mirror = tile.isLocal,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // No video avatar placeholder
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                PulseAvatar(
                    letter = tile.label,
                    pulsing = tile.isSpeaking,
                    size = 56.dp
                )
            }
        }

        // Overlay Gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // Bottom Info Tag (Label + Icons)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (tile.isMuted) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.85f), CircleShape)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Muted",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = tile.label.ifBlank { "مشارك" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isPinned) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = Color(0xFF00FF88),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
