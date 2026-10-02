package com.freedomfighter.mp4remixer.core

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Crops and scales with the phone's own decoder and encoder (Media3 Transformer).
 * The picture is re-encoded in H.264, which every player and messaging app reads;
 * the sound is copied as it is whenever the MP4 container allows it.
 */
@OptIn(UnstableApi::class)
object Remixer {

    /** Transformer wants one thread with a looper for all its calls: the main one. */
    suspend fun remix(
        context: Context,
        source: Uri,
        plan: Plan,
        output: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.Main) {
        val effects = buildList<Effect> {
            if (plan.cropped) add(Crop(plan.left, plan.right, plan.bottom, plan.top))
            add(
                Presentation.createForWidthAndHeight(
                    plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT,
                )
            )
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), effects))
            .build()
        // H.264 carries no HDR: an HDR recording is brought to standard range
        // instead of coming out washed out.
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        coroutineScope {
            var transformer: Transformer? = null
            val poll = launch {
                val holder = ProgressHolder()
                while (true) {
                    delay(300)
                    val state = transformer?.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                }
            }
            try {
                suspendCancellableCoroutine { continuation ->
                    val built = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = built
                    built.start(composition, output.absolutePath)
                    continuation.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { built.cancel() }
                    }
                }
            } finally {
                poll.cancel()
            }
        }
    }
}
