package com.freedomfighter.mp4remixer.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Puts finished videos into the shared Movies collection. Going through
 * MediaStore means no storage permission is ever needed, the video shows up in
 * the gallery at once, and name collisions are resolved by the system.
 */
object Movies {

    const val FOLDER = "MP4 Remixer"

    /**
     * The entry stays pending, invisible to other apps, until the copy is whole:
     * a failure never leaves a truncated video behind.
     */
    fun save(context: Context, file: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("insert")
        try {
            resolver.openOutputStream(uri).use { output ->
                if (output == null) throw IOException("open")
                file.inputStream().use { it.copyTo(output, 1 shl 16) }
            }
            resolver.update(
                uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null,
            )
            return uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }
}
