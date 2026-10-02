package com.freedomfighter.mp4remixer.core

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.freedomfighter.mp4remixer.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the screen needs to know about a picked video before anything is converted. */
class Probed(
    val name: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val frame: Bitmap?,
)

object Probe {

    private const val FRAME_SIDE = 960

    fun read(context: Context, uri: Uri): Probed {
        var display: String? = null
        var takenAtMillis: Long? = null
        var bytes = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    display = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                    bytes = cursor.longOrNull(OpenableColumns.SIZE) ?: 0L
                    takenAtMillis = cursor.longOrNull(MediaStore.MediaColumns.DATE_TAKEN)
                        ?: cursor.longOrNull(MediaStore.MediaColumns.DATE_ADDED)?.times(1000)
                }
            }
        }

        var width = 0
        var height = 0
        var title: String? = null
        var frame: Bitmap? = null
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val w = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val turned = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0
                width = if (turned) h else w
                height = if (turned) w else h
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()?.takeIf { it.isNotBlank() }
                // A little way in: the very first frame is often black.
                val durationMs = retriever.int(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val atUs = minOf(durationMs / 3, 2000) * 1000L
                frame = runCatching {
                    retriever.getScaledFrameAtTime(
                        atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_SIDE, FRAME_SIDE,
                    )
                }.getOrNull()
            }
        }

        // The system photo picker hides real filenames (it reports "<media id>.mp4"),
        // so for its URIs the title tag, then the capture date, make a better name.
        val name = if (isPhotoPickerUri(uri)) {
            title ?: takenAtMillis?.let { dateName(context, it) } ?: display
        } else {
            display ?: uri.lastPathSegment?.substringAfterLast('/')
        } ?: context.getString(R.string.video_name_prefix)

        return Probed(name, width, height, bytes, frame)
    }

    /** content://media/picker/0/<authority>/media/<id> */
    private fun isPhotoPickerUri(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.firstOrNull()?.startsWith("picker") == true

    private fun dateName(context: Context, millis: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(millis))
        return "${context.getString(R.string.video_name_prefix)} $stamp"
    }

    private fun MediaMetadataRetriever.int(key: Int): Int = extractMetadata(key)?.toIntOrNull() ?: 0

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it > 0 } else null
    }
}
