package com.freedomfighter.mp4remixer.ui

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.freedomfighter.mp4remixer.R
import com.freedomfighter.mp4remixer.core.Clip
import com.freedomfighter.mp4remixer.core.Limit
import com.freedomfighter.mp4remixer.core.Movies
import com.freedomfighter.mp4remixer.core.Plan
import com.freedomfighter.mp4remixer.core.Planner
import com.freedomfighter.mp4remixer.core.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemixScreen(
    clips: List<Clip>,
    square: Boolean,
    limit: Limit,
    isRunning: Boolean,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onSquare: (Boolean) -> Unit,
    onLimit: (Limit) -> Unit,
    onCenter: (Long, Float) -> Unit,
    onRemove: (Long) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onShare: (List<Clip>) -> Unit,
) {
    val done = clips.filter { it.status == Status.DONE && it.outputUri != null }
    val plans = clips.associate { clip ->
        clip.id to if (clip.pending) {
            Planner.plan(clip.width, clip.height, square, limit, clip.center)
        } else {
            null
        }
    }
    val canStart = plans.values.any { it != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (clips.isNotEmpty() && !isRunning) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.clear),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (clips.isNotEmpty()) {
                BottomActions(
                    isRunning = isRunning,
                    canStart = canStart,
                    doneCount = done.size,
                    onPickGallery = onPickGallery,
                    onStart = onStart,
                    onCancel = onCancel,
                    onShareAll = { onShare(done) },
                )
            }
        },
    ) { padding ->
        if (clips.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                onPickGallery = onPickGallery,
                onPickFiles = onPickFiles,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "options") {
                    Options(
                        square = square,
                        limit = limit,
                        enabled = !isRunning,
                        onSquare = onSquare,
                        onLimit = onLimit,
                    )
                }
                items(clips, key = { it.id }) { clip ->
                    ClipCard(
                        clip = clip,
                        plan = plans[clip.id],
                        square = square,
                        isRunning = isRunning,
                        onCenter = { onCenter(clip.id, it) },
                        onRemove = { onRemove(clip.id) },
                        onShare = { onShare(listOf(clip)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Crop,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_subtitle, Movies.FOLDER),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPickGallery,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.choose_videos))
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onPickFiles) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.browse_files))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.credits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun Options(
    square: Boolean,
    limit: Limit,
    enabled: Boolean,
    onSquare: (Boolean) -> Unit,
    onLimit: (Limit) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.option_square), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.option_square_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = square, onCheckedChange = onSquare, enabled = enabled)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.option_size), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.option_size_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Limit.entries.forEach { choice ->
                FilterChip(
                    selected = limit == choice,
                    onClick = { onLimit(choice) },
                    enabled = enabled,
                    label = {
                        Text(
                            if (choice == Limit.ORIGINAL) stringResource(R.string.size_original)
                            else choice.label
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ClipCard(
    clip: Clip,
    plan: Plan?,
    square: Boolean,
    isRunning: Boolean,
    onCenter: (Float) -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (clip.readable) {
                // Once queued, the frame drawn is the one frozen in the clip's own plan.
                val framed = if (clip.pending) square else clip.plan?.cropped == true
                Preview(
                    clip = clip,
                    framed = framed && clip.width != clip.height,
                    movable = clip.pending && !isRunning,
                    onCenter = onCenter,
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (clip.status == Status.DONE) clip.outputName else clip.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusLine(clip, plan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (clip.status == Status.FAILED) {
                        Text(
                            text = failureLine(clip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (clip.status == Status.WORKING) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { clip.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (clip.status == Status.DONE) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onShare,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share))
                    }
                } else if (!isRunning) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.remove))
                    }
                }
            }
        }
    }
}

/**
 * The whole picture, with the part the square keeps left bright. Dragging moves
 * the square along the long side: sideways for a landscape video, up and down
 * for a portrait one.
 */
@Composable
private fun Preview(clip: Clip, framed: Boolean, movable: Boolean, onCenter: (Float) -> Unit) {
    val ratio = clip.width.toFloat() / clip.height
    val landscape = clip.width > clip.height
    // The finger's own count: the clip's value comes back a frame late.
    var center by remember(clip.id) { mutableFloatStateOf(clip.center) }
    val report by rememberUpdatedState(onCenter)

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = min(maxWidth, 260.dp * ratio)
        Box(
            Modifier
                .size(width, width / ratio)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            clip.frame?.let { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (framed) {
                val drag = if (!movable) {
                    Modifier
                } else {
                    Modifier.pointerInput(clip.id, landscape) {
                        fun move(amount: Float) {
                            val travel = if (landscape) size.width - size.height else size.height - size.width
                            if (travel <= 0) return
                            center = (center + amount / travel).coerceIn(0f, 1f)
                            report(center)
                        }
                        if (landscape) {
                            detectHorizontalDragGestures { change, amount -> change.consume(); move(amount) }
                        } else {
                            detectVerticalDragGestures { change, amount -> change.consume(); move(amount) }
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize().then(drag)) {
                    val side = minOf(size.width, size.height)
                    val shade = Color.Black.copy(alpha = 0.62f)
                    val stroke = 2.dp.toPx()
                    if (landscape) {
                        val start = (size.width - side) * center
                        drawRect(shade, Offset.Zero, Size(start, side))
                        drawRect(shade, Offset(start + side, 0f), Size(size.width - start - side, side))
                        drawRect(
                            Color.White, Offset(start + stroke / 2, stroke / 2),
                            Size(side - stroke, side - stroke), style = Stroke(stroke),
                        )
                    } else {
                        val start = (size.height - side) * center
                        drawRect(shade, Offset.Zero, Size(side, start))
                        drawRect(shade, Offset(0f, start + side), Size(side, size.height - start - side))
                        drawRect(
                            Color.White, Offset(stroke / 2, start + stroke / 2),
                            Size(side - stroke, side - stroke), style = Stroke(stroke),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLine(clip: Clip, plan: Plan?): String {
    val context = LocalContext.current
    fun size(bytes: Long) = if (bytes > 0) " · " + Formatter.formatShortFileSize(context, bytes) else ""
    val source = "${clip.width} × ${clip.height}" + size(clip.bytes)
    return when (clip.status) {
        Status.READING -> stringResource(R.string.status_reading)
        Status.QUEUED -> stringResource(R.string.status_queued)
        Status.WORKING -> stringResource(R.string.status_working, (clip.progress * 100).toInt())
        Status.DONE -> stringResource(R.string.status_done, Movies.FOLDER) +
            (clip.plan?.let { "\n${it.width} × ${it.height}" + size(clip.outputBytes) } ?: "")
        else -> when {
            !clip.readable -> ""
            plan != null -> "$source  →  ${plan.width} × ${plan.height}"
            else -> source + "\n" + stringResource(R.string.status_nothing)
        } + if (clip.status == Status.CANCELLED) "\n" + stringResource(R.string.status_cancelled) else ""
    }
}

@Composable
private fun failureLine(clip: Clip): String {
    val phrase = stringResource(if (clip.errorRes != 0) clip.errorRes else R.string.status_failed)
    return if (clip.errorDetail.isNullOrBlank()) phrase else "$phrase (${clip.errorDetail})"
}

@Composable
private fun BottomActions(
    isRunning: Boolean,
    canStart: Boolean,
    doneCount: Int,
    onPickGallery: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShareAll: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRunning) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_videos))
                }
                if (canStart) {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.start))
                    }
                } else if (doneCount > 1) {
                    Button(onClick = onShareAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_all, doneCount))
                    }
                }
            }
        }
    }
}
