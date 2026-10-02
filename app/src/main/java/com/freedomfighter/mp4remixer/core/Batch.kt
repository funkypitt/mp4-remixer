package com.freedomfighter.mp4remixer.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.freedomfighter.mp4remixer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class Status { READING, READY, QUEUED, WORKING, DONE, FAILED, CANCELLED }

data class Clip(
    val id: Long,
    val source: Uri,
    val name: String = "",
    /** As displayed, rotation applied. Zero until the video has been read. */
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
    val frame: Bitmap? = null,
    /** Where the square sits along the long side, 0..1. */
    val center: Float = 0.5f,
    val status: Status = Status.READING,
    val plan: Plan? = null,
    val outputName: String = "",
    val progress: Float = 0f,
    val outputUri: Uri? = null,
    val outputBytes: Long = 0,
    val errorRes: Int = 0,
    val errorDetail: String? = null,
) {
    val readable: Boolean get() = width > 0 && height > 0

    /** Still to be done: its settings can change and Start takes it. */
    val pending: Boolean
        get() = readable && (status == Status.READY || status == Status.FAILED || status == Status.CANCELLED)
}

/**
 * The single source of truth for the current batch, shared between the service
 * and the screen. In memory only: a batch is a one-shot thing.
 */
object Batch {

    private const val PREFS = "settings"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(1)

    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    val clips: StateFlow<List<Clip>> = _clips.asStateFlow()

    private val _square = MutableStateFlow(true)
    val square: StateFlow<Boolean> = _square.asStateFlow()

    private val _limit = MutableStateFlow(Limit.ORIGINAL)
    val limit: StateFlow<Limit> = _limit.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var loaded = false

    /** The choices of the last time are the likeliest ones this time. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _square.value = prefs.getBoolean("square", true)
        _limit.value = runCatching { Limit.valueOf(prefs.getString("limit", null) ?: "") }
            .getOrDefault(Limit.ORIGINAL)
    }

    fun setSquare(context: Context, value: Boolean) {
        _square.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("square", value).apply()
    }

    fun setLimit(context: Context, value: Limit) {
        _limit.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("limit", value.name).apply()
    }

    fun add(context: Context, uris: List<Uri>) {
        val app = context.applicationContext
        val known = _clips.value.map { it.source }.toSet()
        val fresh = uris.distinct().filter { it !in known }.map { Clip(id = nextId.getAndIncrement(), source = it) }
        if (fresh.isEmpty()) return
        _clips.update { it + fresh }
        fresh.forEach { clip ->
            scope.launch {
                val probed = Probe.read(app, clip.source)
                update(clip.id) {
                    if (probed.width > 0) {
                        it.copy(
                            name = probed.name, width = probed.width, height = probed.height,
                            bytes = probed.bytes, frame = probed.frame, status = Status.READY,
                        )
                    } else {
                        it.copy(name = probed.name, status = Status.FAILED, errorRes = R.string.status_unreadable)
                    }
                }
            }
        }
    }

    fun setCenter(id: Long, center: Float) = update(id) { if (it.pending) it.copy(center = center) else it }

    fun remove(id: Long) = _clips.update { current -> current.filterNot { it.id == id } }

    fun planFor(clip: Clip): Plan? =
        Planner.plan(clip.width, clip.height, _square.value, _limit.value, clip.center)

    /** Freezes the settings into every clip that has something to gain. Returns how many. */
    fun queue(): Int {
        var count = 0
        val limit = _limit.value
        _clips.update { current ->
            current.map { clip ->
                val plan = if (clip.pending) planFor(clip) else null
                if (plan == null) {
                    clip
                } else {
                    count++
                    clip.copy(
                        status = Status.QUEUED, plan = plan, progress = 0f,
                        outputName = Planner.outputName(clip.name, plan, limit),
                        errorRes = 0, errorDetail = null,
                    )
                }
            }
        }
        return count
    }

    fun nextQueued(): Clip? = _clips.value.firstOrNull { it.status == Status.QUEUED }

    fun update(id: Long, transform: (Clip) -> Clip) {
        _clips.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    /** Marks everything not yet finished as cancelled. */
    fun cancelPending() {
        _clips.update { current ->
            current.map {
                if (it.status == Status.QUEUED || it.status == Status.WORKING) {
                    it.copy(status = Status.CANCELLED, progress = 0f)
                } else {
                    it
                }
            }
        }
    }

    fun clear() {
        _clips.value = emptyList()
    }
}
