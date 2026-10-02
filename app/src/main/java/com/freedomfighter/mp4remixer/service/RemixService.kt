package com.freedomfighter.mp4remixer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media3.transformer.ExportException
import com.freedomfighter.mp4remixer.MainActivity
import com.freedomfighter.mp4remixer.R
import com.freedomfighter.mp4remixer.core.Batch
import com.freedomfighter.mp4remixer.core.Clip
import com.freedomfighter.mp4remixer.core.Movies
import com.freedomfighter.mp4remixer.core.Remixer
import com.freedomfighter.mp4remixer.core.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Runs the batch in the foreground so that leaving the app (to answer a
 * message, to lock the screen) does not stop a video halfway.
 */
class RemixService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Recorded first, so a worker about to stop can tell that newer work has arrived.
        lastStartId = startId

        if (intent?.action == ACTION_CANCEL) {
            worker?.cancel()
            Batch.cancelPending()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID, buildNotification(null, 0, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (worker?.isActive != true) {
            worker = scope.launch { runBatch() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runBatch() {
        Batch.setRunning(true)
        workDir().deleteRecursively()
        try {
            while (coroutineContext.isActive) {
                val clip = Batch.nextQueued()
                if (clip == null) {
                    // Queue drained. Only stop if no start command landed meanwhile.
                    if (stopSelfResult(lastStartId)) break
                    delay(100)
                    continue
                }
                acquireWakeLock()
                remix(clip)
                finished++
            }
        } finally {
            releaseWakeLock()
            workDir().deleteRecursively()
            Batch.setRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private suspend fun remix(clip: Clip) {
        val plan = clip.plan ?: return
        Batch.update(clip.id) { it.copy(status = Status.WORKING, progress = 0f) }
        lastNotifiedPercent = -1
        notifyProgress(clip.name, 0f)

        // Transformer writes to a path: the video is made in the app's cache,
        // then copied into Movies in one go.
        val temp = File(workDir().apply { mkdirs() }, "${clip.id}.mp4")
        try {
            Remixer.remix(this, clip.source, plan, temp) { p ->
                Batch.update(clip.id) { it.copy(progress = p) }
                notifyProgress(clip.name, p)
            }
            val bytes = temp.length()
            val uri = try {
                Movies.save(this, temp, clip.outputName)
            } catch (e: Exception) {
                Batch.update(clip.id) {
                    it.copy(status = Status.FAILED, errorRes = R.string.status_not_saved, errorDetail = e.message)
                }
                return
            }
            Batch.update(clip.id) {
                it.copy(status = Status.DONE, progress = 1f, outputUri = uri, outputBytes = bytes)
            }
        } catch (e: CancellationException) {
            Batch.update(clip.id) { it.copy(status = Status.CANCELLED, progress = 0f) }
            throw e
        } catch (e: Exception) {
            Batch.update(clip.id) {
                it.copy(
                    status = Status.FAILED,
                    errorRes = R.string.status_failed,
                    errorDetail = (e as? ExportException)?.errorCodeName ?: e.message,
                )
            }
        } finally {
            temp.delete()
        }
    }

    private fun workDir() = File(cacheDir, "remix")

    // --- notification ------------------------------------------------------

    private var lastNotifiedPercent = -1

    /** Videos gone through since the service started, whatever their fate. */
    private var finished = 0

    private fun notifyProgress(name: String, progress: Float) {
        val percent = (progress * 100).toInt()
        if (percent == lastNotifiedPercent) return
        lastNotifiedPercent = percent

        val left = Batch.clips.value.count { it.status == Status.QUEUED || it.status == Status.WORKING }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(name, percent, finished + 1, finished + left))
    }

    private fun buildNotification(name: String?, percent: Int, index: Int, total: Int = 0): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, RemixService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (total > 0) getString(R.string.notif_title, index.coerceAtMost(total), total)
                else getString(R.string.notif_title_generic)
            )
            .setContentText(name ?: getString(R.string.notif_starting))
            .setProgress(100, percent, name == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.cancel), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // --- wake lock ---------------------------------------------------------

    /** Taken again for each video, so the time limit applies to one video and not to the batch. */
    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mp4remixer:remix")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(3 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "conversion"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CANCEL = "com.freedomfighter.mp4remixer.CANCEL"

        /** Converts whatever [Batch.queue] has just queued. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RemixService::class.java))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, RemixService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
