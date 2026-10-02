package com.freedomfighter.mp4remixer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedomfighter.mp4remixer.core.Batch
import com.freedomfighter.mp4remixer.core.Sharing
import com.freedomfighter.mp4remixer.service.RemixService
import com.freedomfighter.mp4remixer.ui.Mp4RemixerTheme
import com.freedomfighter.mp4remixer.ui.RemixScreen

class MainActivity : ComponentActivity() {

    private val pickFromGallery = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(pickerLimit())
    ) { uris -> Batch.add(this, uris) }

    private val pickFromFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> Batch.add(this, uris) }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the conversion works either way; the notification is a courtesy */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Batch.load(this)

        setContent {
            Mp4RemixerTheme {
                val clips by Batch.clips.collectAsStateWithLifecycle()
                val square by Batch.square.collectAsStateWithLifecycle()
                val limit by Batch.limit.collectAsStateWithLifecycle()
                val isRunning by Batch.isRunning.collectAsStateWithLifecycle()

                RemixScreen(
                    clips = clips,
                    square = square,
                    limit = limit,
                    isRunning = isRunning,
                    onPickGallery = {
                        pickFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickFiles = { pickFromFiles.launch(arrayOf("video/*")) },
                    onSquare = { Batch.setSquare(this, it) },
                    onLimit = { Batch.setLimit(this, it) },
                    onCenter = { id, center -> Batch.setCenter(id, center) },
                    onRemove = { Batch.remove(it) },
                    onStart = { start() },
                    onCancel = { RemixService.cancel(this) },
                    onClear = { Batch.clear() },
                    onShare = { Sharing.share(this, it) },
                )
            }
        }

        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Videos sent here from another app's share sheet. */
    private fun handleShare(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableListExtra(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        Batch.add(this, uris)
        // Do not re-run on configuration change or task resume.
        intent?.action = null
    }

    private fun start() {
        if (Batch.queue() == 0) return
        // Asked here rather than at launch: by now there is something to
        // notify about, so the prompt has a visible reason.
        askForNotificationsOnce()
        RemixService.start(this)
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (asked) return
        asked = true
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name)
        }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableListExtra(name: String): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(name, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(name).orEmpty()
        }

    private companion object {
        /** Asked at most once per process: a refusal should not become nagging. */
        private var asked = false

        /**
         * The system photo picker rejects a limit above its own maximum, and
         * that maximum is only knowable at runtime.
         */
        fun pickerLimit(): Int {
            val ceiling = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { MediaStore.getPickImagesMaxLimit() }.getOrDefault(100)
            } else {
                100
            }
            return ceiling.coerceIn(2, 100)
        }
    }
}
