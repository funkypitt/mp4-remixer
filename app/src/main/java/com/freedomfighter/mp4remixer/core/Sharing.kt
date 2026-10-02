package com.freedomfighter.mp4remixer.core

import android.content.Context
import android.content.Intent
import com.freedomfighter.mp4remixer.R

/**
 * MediaStore URIs are directly shareable, so no FileProvider is involved:
 * whatever the user picks in the chooser gets a temporary read grant on the
 * real file in Movies.
 */
object Sharing {

    fun share(context: Context, clips: List<Clip>) {
        val uris = clips.mapNotNull { it.outputUri }
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "video/mp4"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Without this the chooser preview falls back to the MediaStore row id.
        if (clips.size == 1) {
            intent.putExtra(Intent.EXTRA_TITLE, clips.first().outputName)
            intent.putExtra(Intent.EXTRA_SUBJECT, clips.first().outputName)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
